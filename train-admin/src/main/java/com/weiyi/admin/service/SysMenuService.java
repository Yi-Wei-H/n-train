package com.weiyi.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.admin.dto.MenuNodeDTO;
import com.weiyi.admin.infra.entity.SysMenu;
import com.weiyi.admin.infra.entity.SysRoleMenu;
import com.weiyi.admin.infra.mapper.SysMenuMapper;
import com.weiyi.admin.infra.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 菜单管理（目录/菜单/按钮三级）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public List<MenuNodeDTO> tree() {
        List<SysMenu> menus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getOrderNum));
        List<MenuNodeDTO> tree = MenuTreeBuilder.build(menus);
        log.debug("菜单树查询：加载 {} 条菜单，根节点 {} 个", menus.size(), tree.size());
        return tree;
    }

    @Transactional
    public MenuNodeDTO save(MenuNodeDTO cmd) {
        SysMenu menu = new SysMenu();
        apply(cmd, menu);
        menuMapper.insert(menu);
        log.info("菜单新增：id={} parentId={} 名称={} 类型={} 权限标识={}",
                menu.getId(), menu.getParentId(), menu.getMenuName(), menu.getMenuType(), menu.getPerms());
        return MenuTreeBuilder.toNode(menu);
    }

    @Transactional
    public MenuNodeDTO update(MenuNodeDTO cmd) {
        SysMenu menu = menuMapper.selectById(cmd.getId());
        BizAssert.notNull(menu, ErrorCode.NOT_FOUND);
        log.info("菜单修改：id={} 名称 {} → {} 权限标识 {} → {}",
                menu.getId(), menu.getMenuName(), cmd.getMenuName(), menu.getPerms(), cmd.getPerms());
        apply(cmd, menu);
        menuMapper.updateById(menu);
        return MenuTreeBuilder.toNode(menu);
    }

    @Transactional
    public void delete(long id) {
        Long children = menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, id));
        BizAssert.isTrue(children == 0, ErrorCode.PARAM_ERROR, "存在子菜单，先删除子级");
        menuMapper.deleteById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getMenuId, id));
        log.info("菜单删除：id={}（同时解除角色关联）", id);
    }

    private void apply(MenuNodeDTO cmd, SysMenu menu) {
        menu.setParentId(cmd.getParentId() == null ? 0L : cmd.getParentId());
        menu.setMenuName(cmd.getMenuName());
        menu.setMenuType(cmd.getMenuType());
        menu.setPerms(cmd.getPerms());
        menu.setPath(cmd.getPath());
        menu.setComponent(cmd.getComponent());
        menu.setIcon(cmd.getIcon());
        menu.setOrderNum(cmd.getOrderNum() == null ? 0 : cmd.getOrderNum());
        menu.setVisible(cmd.getVisible() == null ? 1 : cmd.getVisible());
        menu.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
    }
}
