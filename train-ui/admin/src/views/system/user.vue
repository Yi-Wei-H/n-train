<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="登录名" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button v-perm="'sys:user:add'" type="primary" plain @click="openAdd">
            <el-icon><Plus /></el-icon>新增
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="username" label="登录名" min-width="120" />
        <el-table-column prop="nickname" label="显示名" min-width="140" />
        <el-table-column prop="deptName" label="部门" min-width="150" />
        <el-table-column prop="phone" label="手机号" width="140" class-name="mono" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.userType === 'S' ? 'danger' : 'info'">
              {{ row.userType === 'S' ? '超管' : '普管' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              class="is-neutral"
              :disabled="isPending(row)"
              :aria-busy="isPending(row) ? 'true' : 'false'"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="编辑" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:user:edit'"
                aria-label="编辑"
                @click="openEdit(row)"
              ><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip content="重置密码" placement="top" :show-after="0">
              <el-button
                class="row-action"
                text
                circle
                v-perm="'sys:user:resetPwd'"
                aria-label="重置密码"
                @click="resetPwd(row)"
              ><el-icon><Key /></el-icon></el-button>
            </el-tooltip>
            <el-popconfirm title="确认删除？" @confirm="remove(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="删除" placement="top" :show-after="0">
                    <el-button
                      class="row-action is-danger"
                      text
                      circle
                      v-perm="'sys:user:delete'"
                      aria-label="删除"
                    ><el-icon><Delete /></el-icon></el-button>
                  </el-tooltip>
                </span>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-foot">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          v-model:current-page="query.pageNum"
          @current-change="load"
        />
      </div>
    </el-card>
  </div>

  <el-dialog v-model="dialog" :title="form.id ? '编辑管理员' : '新增管理员'" width="560px" top="5vh" class="form-dialog" destroy-on-close>
    <el-form :model="form" label-width="90px">
      <el-form-item label="登录名"><el-input v-model="form.username" /></el-form-item>
      <el-form-item label="显示名"><el-input v-model="form.nickname" /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
      <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
      <el-form-item label="部门">
        <el-tree-select
          v-model="form.deptId"
          :data="deptTree"
          :props="{ label: 'deptName', value: 'id', children: 'children' }"
          node-key="id" check-strictly style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-radio-group v-model="form.userType">
          <el-radio value="A">普通管理员</el-radio>
          <el-radio value="S">超级管理员</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="!form.id" label="初始密码"><el-input v-model="form.password" /></el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.roleIds" multiple style="width: 100%">
          <el-option v-for="r in roles" :key="r.id" :value="r.id" :label="r.roleName" />
        </el-select>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import { useOptimisticSwitch } from '../../composables/useOptimisticSwitch'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const deptTree = ref([])
const roles = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, username: '' })
const emptyForm = { id: null, username: '', nickname: '', phone: '', email: '', deptId: null, userType: 'A', password: '', roleIds: [] }
const form = reactive({ ...emptyForm })

async function load() {
  loading.value = true
  try {
    const page = await request.get('/system/user/page', { params: query })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function loadRefs() {
  deptTree.value = await request.get('/system/dept/tree')
  roles.value = await request.get('/system/role/list')
}

function openAdd() {
  Object.assign(form, emptyForm, { roleIds: [] })
  dialog.value = true
}

function openEdit(row) {
  request.get(`/system/user/${row.id}`).then((detail) => {
    Object.assign(form, emptyForm, detail, { password: '' })
    dialog.value = true
  })
}

async function save() {
  if (form.id) {
    await request.put('/system/user', form)
  } else {
    await request.post('/system/user', form)
  }
  ElMessage.success('保存成功')
  dialog.value = false
  load()
}

const { isPending, toggle: toggleSwitch } = useOptimisticSwitch(
  (row, enabled) => request.put(`/system/user/${row.id}/status`, null, { params: { status: enabled ? 1 : 0 } }),
  { onSuccess: () => ElMessage.success('已更新') }
)

function toggleStatus(row, enabled) {
  return toggleSwitch(row, enabled)
}

async function resetPwd(row) {
  await ElMessageBox.confirm(
    `确认将 ${row.username} 的密码重置为默认密码？重置后该账号需使用默认密码重新登录`,
    '重置密码',
    { type: 'warning', confirmButtonText: '重置密码', cancelButtonText: '取消' }
  )
  await request.put(`/system/user/${row.id}/password`)
  ElMessage.success('已重置为默认密码')
}

async function remove(row) {
  await request.delete(`/system/user/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(() => { load(); loadRefs() })
</script>
