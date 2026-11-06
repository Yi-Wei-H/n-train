<template>
  <div class="page">
    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <span class="table-title">菜单树</span>
        <span class="spacer"></span>
        <el-button type="primary" plain v-perm="'sys:menu:add'" @click="openAdd(null)">
          <el-icon><Plus /></el-icon>新增目录
        </el-button>
      </div>
      <el-table
        :data="rows"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        v-loading="loading"
      >
        <el-table-column prop="menuName" label="名称" width="200" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="{ M: 'warning', C: 'success', F: 'info' }[row.menuType]">
              {{ { M: '目录', C: '菜单', F: '按钮' }[row.menuType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="perms" label="权限标识" width="190" class-name="mono" />
        <el-table-column prop="path" label="路由" width="150" class-name="mono" />
        <el-table-column prop="component" label="组件" min-width="190" class-name="mono" />
        <el-table-column prop="orderNum" label="排序" width="80" align="right" class-name="mono" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip v-if="row.menuType !== 'F'" content="新增下级" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:menu:add'"
                aria-label="新增下级"
                @click="openAdd(row)"
              ><el-icon><Plus /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:menu:edit'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'sys:menu:delete'"
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

  <el-dialog v-model="dialog" :title="form.id ? '编辑菜单' : '新增菜单'" width="560px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="上级菜单">
        <el-tree-select
          v-model="form.parentId"
          :data="rows"
          :props="{ label: 'menuName', value: 'id', children: 'children' }"
          node-key="id" check-strictly style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="名称"><el-input v-model="form.menuName" /></el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="form.menuType">
          <el-radio value="M">目录</el-radio>
          <el-radio value="C">菜单</el-radio>
          <el-radio value="F">按钮</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="权限标识"><el-input v-model="form.perms" placeholder="如 sys:user:add" /></el-form-item>
      <el-form-item v-if="form.menuType !== 'F'" label="路由地址"><el-input v-model="form.path" /></el-form-item>
      <el-form-item v-if="form.menuType === 'C'" label="组件路径"><el-input v-model="form.component" placeholder="如 system/user" /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.orderNum" :min="0" /></el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
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
const emptyForm = {
  id: null, parentId: 0, menuName: '', menuType: 'M', perms: '',
  path: '', component: '', orderNum: 0, visible: 1, status: 1
}
const form = reactive({ ...emptyForm })

async function load() {
  loading.value = true
  try {
    rows.value = await request.get('/system/menu/tree')
  } finally {
    loading.value = false
  }
}

function openAdd(parent) {
  Object.assign(form, emptyForm, { parentId: parent ? parent.id : 0 })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm, row)
  dialog.value = true
}

async function save() {
  if (form.id) await request.put('/system/menu', form)
  else await request.post('/system/menu', form)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

async function remove(row) {
  await request.delete(`/system/menu/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
