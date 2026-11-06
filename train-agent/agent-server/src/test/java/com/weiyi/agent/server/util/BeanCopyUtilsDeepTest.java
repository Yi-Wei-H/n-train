package com.weiyi.agent.server.util;

import com.weiyi.common.core.util.BeanCopyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BeanCopyUtils 深拷贝语义测试：任意对象 → 任意目标类，不依赖 Spring/数据库。
 */
class BeanCopyUtilsDeepTest {

    // ---------- 测试夹具：跨类嵌套 + 继承 + 泛型集合 ----------

    static class Child {
        private String name;
        private LocalDateTime time;

        Child() {
        }

        Child(String name, LocalDateTime time) {
            this.name = name;
            this.time = time;
        }
    }

    static class ChildDTO {
        private String name;
        private LocalDateTime time;
    }

    static class Base {
        protected Long id;
    }

    static class Order extends Base {
        private String title;
        private List<Child> items = new ArrayList<>();
        private Map<String, Child> byName = new HashMap<>();
        private Child[] slots;
        private Child extra;
    }

    static class OrderDTO extends Base {
        private String title;
        private List<ChildDTO> items;
        private Map<String, ChildDTO> byName;
        private ChildDTO[] slots;
        private ChildDTO extra;
        private Integer score; // source 没有的字段：保持 null
    }

    static class Node {
        private String label;
        private Node next;
    }

    @Test
    @DisplayName("跨类嵌套深拷贝：bean/集合/Map/数组逐层重建，改副本不影响源")
    void testDeepCopyCrossClassNested() {
        LocalDateTime time = LocalDateTime.of(2026, 10, 21, 10, 0);
        Order order = buildOrder(time);

        OrderDTO dto = BeanCopyUtils.deepCopy(order, OrderDTO.class);

        assertEquals("查票", dto.title);
        assertEquals(9L, dto.id);
        assertEquals(2, dto.items.size());
        assertEquals("b", dto.items.get(1).name);
        assertEquals(time, dto.items.get(1).time);
        assertEquals("a", dto.byName.get("a").name);
        assertEquals("s1", dto.slots[0].name);
        assertEquals("e", dto.extra.name);
        assertNull(dto.score);

        // 深独立性：改副本任何一层都不回写源
        dto.items.get(0).name = "changed";
        dto.byName.get("a").name = "changed";
        dto.slots[0].name = "changed";
        dto.extra.name = "changed";
        assertEquals("a", order.items.get(0).name);
        assertEquals("a", order.byName.get("a").name);
        assertEquals("s1", order.slots[0].name);
        assertEquals("e", order.extra.name);
    }

    @Test
    @DisplayName("同类型深克隆：deepCopy(obj, obj.getClass())")
    void testDeepCopySameType() {
        Order order = buildOrder(LocalDateTime.now());
        Order clone = BeanCopyUtils.deepCopy(order, Order.class);

        assertNotSame(order, clone);
        assertNotSame(order.items, clone.items);
        assertNotSame(order.items.get(0), clone.items.get(0));
        assertEquals(order.items.get(0).name, clone.items.get(0).name);
    }

    @Test
    @DisplayName("不可变值共享引用：String/java.time 不重建（深拷贝的安全等价）")
    void testImmutableShared() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);
        Child child = new Child("x", time);
        ChildDTO dto = BeanCopyUtils.deepCopy(child, ChildDTO.class);

        assertSame("x", dto.name); // 字符串常量池同引用
        assertSame(time, dto.time);
        assertNotSame(child, dto);
    }

    @Test
    @DisplayName("循环引用安全：自指节点不栈溢出，副本保持自指结构")
    void testCircularReference() {
        Node node = new Node();
        node.label = "root";
        node.next = node; // 自指

        Node copy = BeanCopyUtils.deepCopy(node, Node.class);

        assertEquals("root", copy.label);
        assertSame(copy, copy.next, "自指关系在副本中保持");
        assertNotSame(node, copy);
    }

    @Test
    @DisplayName("源对象图内的共享关系在副本中保持（同一 list 两个引用只拷一次）")
    void testSharedReferencePreserved() {
        Order order = new Order();
        order.id = 1L;
        order.title = "t";
        List<Child> shared = new ArrayList<>(List.of(new Child("s", null)));
        order.items = shared;
        order.extra = new Child();
        // 借 items 验证：deepCopyList 中相同元素引用复用
        List<Order> source = List.of(order, order);

        List<Order> copies = BeanCopyUtils.deepCopyList(source, Order.class);

        assertSame(copies.get(0), copies.get(1), "相同 source 引用只拷一次并复用");
        assertEquals("s", copies.get(0).items.get(0).name);
    }

    @Test
    @DisplayName("List 深拷贝跨类元素 + null 元素与 null 源")
    void testDeepCopyList() {
        List<Child> list = new ArrayList<>();
        list.add(new Child("a", null));
        list.add(null);

        List<ChildDTO> copies = BeanCopyUtils.deepCopyList(list, ChildDTO.class);

        assertEquals(2, copies.size());
        assertEquals("a", copies.get(0).name);
        assertNull(copies.get(1));
        assertTrue(BeanCopyUtils.deepCopyList(null, ChildDTO.class).isEmpty());
        assertNull(BeanCopyUtils.deepCopy(null, ChildDTO.class));
    }

    @Test
    @DisplayName("字段声明为父类/Object：按值运行时类型深克隆，保持具体类型")
    void testPolymorphicField() {
        Holder holder = new Holder();
        holder.any = new Child("poly", null);

        Holder copy = BeanCopyUtils.deepCopy(holder, Holder.class);

        assertNotSame(holder.any, copy.any);
        assertTrue(copy.any instanceof Child);
        assertEquals("poly", ((Child) copy.any).name);
    }

    static class Holder {
        private Object any;
    }

    @Test
    @DisplayName("null 字段照拷（全量语义），源缺失字段保持目标默认")
    void testNullFieldsCopied() {
        Child child = new Child("only-name", null);

        ChildDTO dto = BeanCopyUtils.deepCopy(child, ChildDTO.class);

        assertEquals("only-name", dto.name);
        assertNull(dto.time);
    }

    private Order buildOrder(LocalDateTime time) {
        Order order = new Order();
        order.id = 9L;
        order.title = "查票";
        Child a = new Child("a", time);
        Child b = new Child("b", time);
        order.items.add(a);
        order.items.add(b);
        order.byName.put("a", a);
        order.byName.put("b", b);
        order.slots = new Child[]{new Child("s1", time), null};
        order.extra = new Child("e", time);
        return order;
    }
}
