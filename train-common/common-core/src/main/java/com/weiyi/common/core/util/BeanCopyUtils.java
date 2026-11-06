package com.weiyi.common.core.util;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用 Bean 拷贝工具（浅拷贝 + 深拷贝）
 * <p>
 * 浅拷贝（同名属性、引用共享）：copy(Object, Object) 全量覆盖、
 * copyNonNull(Object, Object) 跳过 null（更新场景局部覆盖）、
 * copy(Object, Class)/copyList(List, Class) 返回新实例。
 * <p>
 * 深拷贝（嵌套可变结构递归重建）：deepCopy(Object, Class)/deepCopyList(List, Class)
 * 语义约定：
 * <ul>
 * <li>不可变值（String、包装类/Number、Boolean、Character、枚举、Class、java.time 全系、
 *     ZoneId、UUID）直接共享引用——值不可修改，与重建等价且零开销；</li>
 * <li>嵌套 bean 跨类按<b>目标类同名字段</b>递归（source 子对象 A 嵌 B，目标字段类型 C 则逐字段拷入 C）；</li>
 * <li>Collection/Map/数组整体重建；集合元素目标类型取目标字段的泛型签名
 *     （{@code List<ChildB>}），无泛型签名时按元素运行时类型深克隆；</li>
 * <li>字段声明为接口/抽象类/Object 时按值运行时类型深克隆（保持具体类型）；</li>
 * <li>循环引用安全：同一 (source 对象, 目标类型) 只拷贝一次并复用，原对象图中的共享关系在副本中保持；</li>
 * <li>static / final 字段不拷；同名但类型不兼容的字段跳过；null 值照拷（全量语义）。</li>
 * </ul>
 */
public final class BeanCopyUtils {

    /** 按 Class 缓存属性描述，避免每次拷贝重复内省（Introspector 自身也有缓存，双保险） */
    private static final Map<Class<?>, PropertyDescriptor[]> DESCRIPTORS = new ConcurrentHashMap<>();

    /** 按 Class 缓存字段（含父类），深拷贝用 */
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private BeanCopyUtils() {
    }

    // -----------------------------浅拷贝----------------------------------------

    /**
     * 全量浅拷贝：同名且类型兼容的字段全部覆盖（含 null）。
     */
    public static void copy(Object source, Object target) {
        copyInternal(source, target, false);
    }

    /**
     * 非空浅拷贝：source 中为 null 的字段跳过。
     * 注意：本方法无法把字段清空，需要清空的字段请显式 set。
     */
    public static void copyNonNull(Object source, Object target) {
        copyInternal(source, target, true);
    }

    /**
     * 全量浅拷贝并返回目标类新实例（含 null）：Entity → DTO 常用，
     * DTO 只声明需要的字段即天然完成出参裁剪（目标驱动遍历，source 多余字段不会带上）。
     */
    public static <T> T copy(Object source, Class<T> targetClass) {
        T target;
        try {
            target = targetClass.cast(newConstructor(targetClass).newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("实例化失败：" + targetClass.getName(), e);
        }
        copyInternal(source, target, false);
        return target;
    }

    /**
     * List 全量浅拷贝：source 为 null 时返回空列表。
     */
    public static <S, T> List<T> copyList(List<S> source, Class<T> targetClass) {
        if (source == null) {
            return List.of();
        }
        return source.stream().map(s -> copy(s, targetClass)).toList();
    }

    private static void copyInternal(Object source, Object target, boolean skipNull) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("source 与 target 均不能为空");
        }
        PropertyDescriptor[] sourcePds = descriptors(source.getClass());
        for (PropertyDescriptor targetPd : descriptors(target.getClass())) {
            Method write = targetPd.getWriteMethod();
            if (write == null) {
                continue;
            }
            PropertyDescriptor sourcePd = find(sourcePds, targetPd.getName());
            if (sourcePd == null) {
                continue; // source 没有同名字段（如 Req 里的 createdAt/deleted），跳过
            }
            Method read = sourcePd.getReadMethod();
            if (read == null || !write.getParameterTypes()[0].isAssignableFrom(read.getReturnType())) {
                continue;
            }
            try {
                Object value = read.invoke(source);
                if (skipNull && value == null) {
                    continue;
                }
                write.invoke(target, value);
            } catch (ReflectiveOperationException e) {
                // 拷贝坏了宁可失败，也不静默写半截数据
                throw new IllegalStateException("属性拷贝失败：" + targetPd.getName(), e);
            }
        }
    }

    // ----------------------------------深拷贝-----------------------------------

    /**
     * 深拷贝到目标类新实例：嵌套可变结构（bean/集合/Map/数组）递归重建，不可变值共享引用。
     * source 为 null 返回 null。同类深克隆传 {@code deepCopy(obj, obj.getClass())}。
     */
    public static <T> T deepCopy(Object source, Class<T> targetClass) {
        return deepCopyInternal(source, targetClass, new IdentityHashMap<>());
    }

    /**
     * List 深拷贝：source 为 null 时返回空列表；元素间相同引用在副本中保持共享。
     */
    public static <S, T> List<T> deepCopyList(List<S> source, Class<T> targetClass) {
        if (source == null) {
            return List.of();
        }
        Map<Object, Map<Class<?>, Object>> seen = new IdentityHashMap<>();
        List<T> result = new ArrayList<>(source.size());
        for (S s : source) {
            result.add(s == null ? null : deepCopyInternal(s, targetClass, seen));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T deepCopyInternal(Object source, Class<?> targetClass, Map<Object, Map<Class<?>, Object>> seen) {
        if (source == null) {
            return null;
        }
        // 循环引用/共享引用复用：同一 (source, 目标类型) 只拷一次
        Map<Class<?>, Object> byType = seen.get(source);
        if (byType != null) {
            Object cached = byType.get(targetClass);
            if (cached != null) {
                return (T) cached;
            }
        }
        if (isImmutable(source)) {
            return (T) source;
        }
        if (targetClass.isArray()) {
            return (T) deepCopyArray(source, targetClass, seen);
        }
        if (source instanceof Collection<?> coll) {
            return (T) deepCopyCollection(coll, targetClass, null, seen);
        }
        if (source instanceof Map<?, ?> map) {
            return (T) deepCopyMap(map, targetClass, null, null, seen);
        }
        return (T) deepCopyBean(source, targetClass, seen);
    }

    /** 深拷贝一个 bean：先建实例注册进 seen（防循环），再按目标类同名字段填充 */
    private static Object deepCopyBean(Object source, Class<?> targetClass, Map<Object, Map<Class<?>, Object>> seen) {
        Object target = newInstance(targetClass);
        seen.computeIfAbsent(source, k -> new HashMap<>()).put(targetClass, target);
        Class<?> sourceClass = source.getClass();
        for (Field targetField : fields(targetClass)) {
            if (targetField.isSynthetic() || Modifier.isStatic(targetField.getModifiers())
                    || Modifier.isFinal(targetField.getModifiers())) {
                continue;
            }
            Field sourceField = findField(sourceClass, targetField.getName());
            if (sourceField == null) {
                continue; // source 无同名字段
            }
            Object value = read(sourceField, source);
            if (value == null || (targetField.getType().isAssignableFrom(value.getClass()) && isImmutable(value))) {
                write(targetField, target, value);
                continue;
            }
            Object copied = copyFieldValue(value, targetField, seen);
            if (copied != null && targetField.getType().isAssignableFrom(copied.getClass())) {
                write(targetField, target, copied);
            } // 类型不兼容（拷不出可赋值结果）则跳过该字段
        }
        return target;
    }

    /** 字段值深拷贝：集合/Map/数组从字段声明拿泛型签名，普通字段按声明类型递归 */
    private static Object copyFieldValue(Object value, Field targetField, Map<Object, Map<Class<?>, Object>> seen) {
        Class<?> fieldType = targetField.getType();
        if (fieldType.isArray()) {
            return deepCopyInternal(value, fieldType, seen);
        }
        if (Collection.class.isAssignableFrom(fieldType)) {
            return deepCopyCollection((Collection<?>) value, fieldType, genericArg(targetField, 0), seen);
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            return deepCopyMap((Map<?, ?>) value, fieldType, genericArg(targetField, 0), genericArg(targetField, 1), seen);
        }
        // 字段声明为接口/抽象类/Object 且值可赋入 → 按运行时类型深克隆（保持具体类型）
        if (fieldType.isAssignableFrom(value.getClass())
                && (fieldType.isInterface() || Modifier.isAbstract(fieldType.getModifiers()) || fieldType == Object.class)) {
            return deepCopyInternal(value, value.getClass(), seen);
        }
        return deepCopyInternal(value, fieldType, seen);
    }

    private static Object deepCopyCollection(Collection<?> source, Class<?> collectionType, Class<?> elementType,
                                             Map<Object, Map<Class<?>, Object>> seen) {
        Collection<Object> target = newCollection(collectionType);
        seen.computeIfAbsent(source, k -> new HashMap<>()).put(collectionType, target);
        for (Object element : source) {
            target.add(deepCopyElement(element, elementType, seen));
        }
        return target;
    }

    private static Object deepCopyMap(Map<?, ?> source, Class<?> mapType, Class<?> keyType, Class<?> valueType,
                                      Map<Object, Map<Class<?>, Object>> seen) {
        Map<Object, Object> target = newMap(mapType);
        seen.computeIfAbsent(source, k -> new HashMap<>()).put(mapType, target);
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            target.put(deepCopyElement(entry.getKey(), keyType, seen), deepCopyElement(entry.getValue(), valueType, seen));
        }
        return target;
    }

    private static Object deepCopyArray(Object source, Class<?> targetArrayType, Map<Object, Map<Class<?>, Object>> seen) {
        Class<?> componentType = targetArrayType.getComponentType();
        int length = Array.getLength(source);
        Object target = Array.newInstance(componentType, length);
        seen.computeIfAbsent(source, k -> new HashMap<>()).put(targetArrayType, target);
        for (int i = 0; i < length; i++) {
            Object element = Array.get(source, i);
            Object copied = element == null ? null
                    : componentType.isPrimitive()
                            ? deepCopyInternal(element, element.getClass(), seen) // 基本类型数组：元素按运行时类克隆后由 Array.set 自动拆箱
                            : deepCopyInternal(element, chooseElementType(element, componentType), seen);
            if (copied != null || element == null) {
                Array.set(target, i, copied);
            }
        }
        return target;
    }

    /** 集合元素深拷贝：有目标元素类型（泛型签名）按之，否则按运行时类型深克隆 */
    private static Object deepCopyElement(Object element, Class<?> elementType, Map<Object, Map<Class<?>, Object>> seen) {
        if (element == null) {
            return null;
        }
        return deepCopyInternal(element, chooseElementType(element, elementType), seen);
    }

    /**
     * 集合/数组元素的目标类型：具体类一律拷成它（同类型克隆或跨类同名字段拷贝）；
     * 缺省/接口/抽象类/Object 无法实例化，退化为元素运行时类型深克隆。
     */
    private static Class<?> chooseElementType(Object element, Class<?> elementType) {
        if (elementType == null || elementType == Object.class || elementType.isInterface()
                || Modifier.isAbstract(elementType.getModifiers())) {
            return element.getClass();
        }
        return elementType;
    }

    /** 取字段泛型签名第 n 个实参（如 {@code List<ChildB>} 的 ChildB）；非参数化类型返回 null */
    private static Class<?> genericArg(Field field, int index) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized) {
            Type[] args = parameterized.getActualTypeArguments();
            if (args.length > index && args[index] instanceof Class<?> arg) {
                return arg;
            }
        }
        return null;
    }

    private static boolean isImmutable(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>
                || value instanceof TemporalAccessor || value instanceof TemporalAmount
                || value instanceof ZoneId || value instanceof UUID;
    }

    private static Collection<Object> newCollection(Class<?> type) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            if (List.class.isAssignableFrom(type)) {
                return new ArrayList<>();
            }
            if (SortedSet.class.isAssignableFrom(type)) {
                return new TreeSet<>();
            }
            if (Set.class.isAssignableFrom(type)) {
                return new LinkedHashSet<>();
            }
            if (Queue.class.isAssignableFrom(type) || Deque.class.isAssignableFrom(type)) {
                return new ArrayDeque<>();
            }
            return new ArrayList<>();
        }
        try {
            return (Collection<Object>) newConstructor(type).newInstance();
        } catch (ReflectiveOperationException e) {
            return new ArrayList<>(); // 无可用无参构造的具体集合类，兜底
        }
    }

    private static Map<Object, Object> newMap(Class<?> type) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return new LinkedHashMap<>();
        }
        try {
            return (Map<Object, Object>) newConstructor(type).newInstance();
        } catch (ReflectiveOperationException e) {
            return new LinkedHashMap<>();
        }
    }

    private static Object newInstance(Class<?> type) {
        try {
            return newConstructor(type).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("深拷贝实例化失败：" + type.getName(), e);
        }
    }

    /** 非公共类（如包私有嵌套类）的无参构造也需放开访问 */
    private static java.lang.reflect.Constructor<?> newConstructor(Class<?> type) throws NoSuchMethodException {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor;
    }

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("字段读取失败：" + field.getName(), e);
        }
    }

    private static void write(Field field, Object owner, Object value) {
        if (value == null && field.getType().isPrimitive()) {
            return; // 基本类型字段不能写 null（如 Integer 源 → int 目标），保持默认值
        }
        try {
            field.setAccessible(true);
            field.set(owner, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("字段写入失败：" + field.getName(), e);
        }
    }

    /** 含父类的全部声明字段（含私有），按类缓存 */
    private static List<Field> fields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, t -> {
            List<Field> all = new ArrayList<>();
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                all.addAll(List.of(c.getDeclaredFields()));
            }
            return all;
        });
    }

    private static Field findField(Class<?> type, String name) {
        for (Field field : fields(type)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // 浅拷贝辅助
    // ---------------------------------------------------------------------

    /**
     * PropertyDescriptor是一个类中属性的对象，
     * @param type
     * @return
     */
    private static PropertyDescriptor[] descriptors(Class<?> type) {
        return DESCRIPTORS.computeIfAbsent(type, t -> {
            try {
                // stopClass = Object：排除 getClass() 的 class 属性
                return Introspector.getBeanInfo(t, Object.class).getPropertyDescriptors();
            } catch (IntrospectionException e) {
                throw new IllegalStateException("内省失败：" + t.getName(), e);
            }
        });
    }

    private static PropertyDescriptor find(PropertyDescriptor[] pds, String name) {
        for (PropertyDescriptor pd : pds) {
            if (pd.getName().equals(name)) {
                return pd;
            }
        }
        return null;
    }
}
