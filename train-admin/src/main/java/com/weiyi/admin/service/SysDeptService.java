package com.weiyi.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.admin.dto.DeptNodeDTO;
import com.weiyi.admin.infra.entity.SysDept;
import com.weiyi.admin.infra.entity.SysUser;
import com.weiyi.admin.infra.mapper.SysDeptMapper;
import com.weiyi.admin.infra.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门管理（ancestors 部门链：父链 + 父 ID，数据权限前缀匹配用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    public List<DeptNodeDTO> tree() {
        List<SysDept> depts = deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getOrderNum));
        Map<Long, List<DeptNodeDTO>> byParent = depts.stream().map(this::toNode)
                .collect(Collectors.groupingBy(DeptNodeDTO::getParentId));
        return depts.stream().map(this::toNode)
                .peek(node -> node.setChildren(byParent.getOrDefault(node.getId(), List.of())
                        .stream().sorted(Comparator.comparing(DeptNodeDTO::getOrderNum,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .collect(Collectors.toList())))
                .filter(node -> node.getParentId() == null || node.getParentId() == 0L)
                .sorted(Comparator.comparing(DeptNodeDTO::getOrderNum,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Transactional
    public DeptNodeDTO save(DeptNodeDTO cmd) {
        SysDept dept = new SysDept();
        apply(cmd, dept);
        deptMapper.insert(dept);
        log.info("部门新增：id={} parentId={} 名称={} ancestors={}",
                dept.getId(), dept.getParentId(), dept.getDeptName(), dept.getAncestors());
        return toNode(dept);
    }

    @Transactional
    public DeptNodeDTO update(DeptNodeDTO cmd) {
        SysDept dept = deptMapper.selectById(cmd.getId());
        BizAssert.notNull(dept, ErrorCode.NOT_FOUND);
        BizAssert.isTrue(!cmd.getId().equals(cmd.getParentId()), ErrorCode.PARAM_ERROR, "父部门不能是自身");
        log.info("部门修改：id={} parentId {} → {} 名称 {} → {}", dept.getId(), dept.getParentId(),
                cmd.getParentId(), dept.getDeptName(), cmd.getDeptName());
        apply(cmd, dept);
        deptMapper.updateById(dept);
        return toNode(dept);
    }

    @Transactional
    public void delete(long id) {
        Long children = deptMapper.selectCount(Wrappers.<SysDept>lambdaQuery()
                .eq(SysDept::getParentId, id));
        BizAssert.isTrue(children == 0, ErrorCode.PARAM_ERROR, "存在子部门，先删除子级");
        Long users = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getDeptId, id));
        BizAssert.isTrue(users == 0, ErrorCode.PARAM_ERROR, "部门下仍有管理员，先移出");
        deptMapper.deleteById(id);
        log.info("部门删除：id={}", id);
    }

    /** ancestors = 父部门链 + "," + 父部门ID；顶级为 "0" */
    private void apply(DeptNodeDTO cmd, SysDept dept) {
        Long parentId = cmd.getParentId() == null ? 0L : cmd.getParentId();
        dept.setParentId(parentId);
        if (parentId == 0L) {
            dept.setAncestors("0");
        } else {
            SysDept parent = deptMapper.selectById(parentId);
            BizAssert.notNull(parent, ErrorCode.PARAM_ERROR, "父部门不存在");
            dept.setAncestors(parent.getAncestors() + "," + parentId);
        }
        dept.setDeptName(cmd.getDeptName());
        dept.setOrderNum(cmd.getOrderNum() == null ? 0 : cmd.getOrderNum());
        dept.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
    }

    private DeptNodeDTO toNode(SysDept dept) {
        DeptNodeDTO node = new DeptNodeDTO();
        node.setId(dept.getId());
        node.setParentId(dept.getParentId());
        node.setAncestors(dept.getAncestors());
        node.setDeptName(dept.getDeptName());
        node.setOrderNum(dept.getOrderNum());
        node.setStatus(dept.getStatus());
        return node;
    }
}
