<template>
  <div class="page">
    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <span class="table-title">角色列表</span>
        <span class="spacer"></span>
        <el-button type="primary" plain v-perm="'sys:role:add'" @click="openAdd">
          <el-icon><Plus /></el-icon>新增角色
        </el-button>
      </div>
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="roleName" label="角色名" width="180" />
        <el-table-column prop="roleKey" label="角色标识" width="180" class-name="mono" />
        <el-table-column label="内置" width="80" align="center">
          <template #default="{ row }">{{ row.builtIn === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:role:edit'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="分配菜单" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:role:edit'"
                aria-label="分配菜单"
                @click="openMenus(row)"
              ><el-icon><Operation /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'sys:role:delete'"
                      aria-label="删除"
                    ><el-icon><Delete /></el-icon></el-button>
                  </el-tooltip>
                </span>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>

  <el-dialog v-model="dialog" :title="form.id ? '编辑角色' : '新增角色'" width="480px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="角色名"><el-input v-model="form.roleName" /></el-form-item>
      <el-form-item label="角色标识"><el-input v-model="form.roleKey" :disabled="form.builtIn === 1" /></el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="menuDialog" :title="`分配菜单 - ${current?.roleName || ''}`" width="480px" top="5vh" class="form-dialog" destroy-on-close>
    <el-tree
      ref="menuTreeRef"
      :data="menuTree"
      show-checkbox
      node-key="id"
      :props="{ label: 'menuName', children: 'children' }"
      default-expand-all
      :default-checked-keys="checkedMenuIds"
    />
    <template #footer>
      <el-button @click="menuDialog = false">取消</el-button>
      <el-button type="primary" @click="saveMenus">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const menuDialog = ref(false)
const menuTree = ref([])
const checkedMenuIds = ref([])
const current = ref(null)
const menuTreeRef = ref()
const emptyForm = { id: null, roleName: '', roleKey: '', builtIn: 0, status: 1, remark: '' }
const form = reactive({ ...emptyForm })

async function load() {
  loading.value = true
  try {
    rows.value = await request.get('/system/role/list')
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, emptyForm)
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm, row)
  dialog.value = true
}

async function save() {
  if (form.id) await request.put('/system/role', form)
  else await request.post('/system/role', form)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

function leafIds(nodes = []) {
  const ids = []
  for (const node of nodes) {
    if (node.children?.length) ids.push(...leafIds(node.children))
    else ids.push(node.id)
  }
  return ids
}

async function openMenus(row) {
  current.value = row
  const [tree, assigned] = await Promise.all([
    request.get('/system/menu/tree'),
    request.get(`/system/role/${row.id}/menu-ids`)
  ])
  const assignedSet = new Set((assigned || []).map(String))
  menuTree.value = tree
  checkedMenuIds.value = leafIds(tree).filter((id) => assignedSet.has(String(id)))
  menuDialog.value = true
}

async function saveMenus() {
  const keys = menuTreeRef.value.getCheckedKeys().concat(menuTreeRef.value.getHalfCheckedKeys())
  await request.put(`/system/role/${current.value.id}/menus`, keys)
  ElMessage.success('已保存')
  menuDialog.value = false
}

async function remove(row) {
  await request.delete(`/system/role/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
