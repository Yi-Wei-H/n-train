<template>
  <div class="page">
    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <span class="table-title">部门树</span>
        <span class="spacer"></span>
        <el-button type="primary" plain v-perm="'sys:dept:add'" @click="openAdd(null)">
          <el-icon><Plus /></el-icon>新增部门
        </el-button>
      </div>
      <el-table
        :data="rows"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        v-loading="loading"
      >
        <el-table-column prop="deptName" label="部门名称" width="240" />
        <el-table-column prop="ancestors" label="部门链" min-width="240" class-name="mono" />
        <el-table-column prop="orderNum" label="排序" width="80" align="right" class-name="mono" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="新增下级" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:dept:add'"
                aria-label="新增下级"
                @click="openAdd(row)"
              ><el-icon><Plus /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:dept:edit'"
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
                      v-perm="'sys:dept:delete'"
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

  <el-dialog v-model="dialog" :title="form.id ? '编辑部门' : '新增部门'" width="480px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="上级部门">
        <el-tree-select
          v-model="form.parentId"
          :data="rows"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          node-key="id" check-strictly style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="部门名称"><el-input v-model="form.deptName" /></el-form-item>
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
const emptyForm = { id: null, parentId: 0, deptName: '', orderNum: 0, status: 1 }
const form = reactive({ ...emptyForm })

async function load() {
  loading.value = true
  try {
    rows.value = await request.get('/system/dept/tree')
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
  if (form.id) await request.put('/system/dept', form)
  else await request.post('/system/dept', form)
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

async function remove(row) {
  await request.delete(`/system/dept/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>
