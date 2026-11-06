package com.weiyi.admin.service;

import com.weiyi.admin.dto.MenuNodeDTO;
import com.weiyi.admin.infra.entity.SysMenu;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SysMenu 实体 → MenuNodeDTO 树。
 */
final class MenuTreeBuilder {

    private MenuTreeBuilder() {
    }

    static List<MenuNodeDTO> build(List<SysMenu> menus) {
        List<MenuNodeDTO> nodes = menus.stream().map(MenuTreeBuilder::toNode).toList();
        Map<Long, List<MenuNodeDTO>> byParent = nodes.stream()
                .collect(Collectors.groupingBy(MenuNodeDTO::getParentId));
        nodes.forEach(node -> node.setChildren(byParent.getOrDefault(node.getId(), List.of())
                .stream().sorted(Comparator.comparing(MenuNodeDTO::getOrderNum,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList())));
        return nodes.stream()
                .filter(node -> node.getParentId() == null || node.getParentId() == 0L)
                .sorted(Comparator.comparing(MenuNodeDTO::getOrderNum,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    static MenuNodeDTO toNode(SysMenu menu) {
        MenuNodeDTO node = new MenuNodeDTO();
        node.setId(menu.getId());
        node.setParentId(menu.getParentId());
        node.setMenuName(menu.getMenuName());
        node.setMenuType(menu.getMenuType());
        node.setPerms(menu.getPerms());
        node.setPath(menu.getPath());
        node.setComponent(menu.getComponent());
        node.setIcon(menu.getIcon());
        node.setOrderNum(menu.getOrderNum());
        node.setVisible(menu.getVisible());
        node.setStatus(menu.getStatus());
        return node;
    }
}
