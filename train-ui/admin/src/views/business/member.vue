<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading">
        <el-table-column prop="id" label="ID" min-width="190" class-name="mono" />
        <el-table-column prop="phone" label="手机号" width="150" class-name="mono">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="150" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="190" class-name="mono" />
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              :title="row.status === 1 ? '确认禁用该会员？' : '确认恢复该会员？'"
              @confirm="toggle(row)"
            >
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip :content="row.status === 1 ? '禁用会员' : '恢复会员'" placement="top" :show-after="0">
                    <el-button
                      class="row-action"
                      :class="{ 'is-danger': row.status === 1 }"
                      text
                      circle
                      v-perm="'m:member:edit'"
                      :aria-label="row.status === 1 ? '禁用会员' : '恢复会员'"
                    ><el-icon><CircleClose v-if="row.status === 1" /><CircleCheck v-else /></el-icon></el-button>
                  </el-tooltip>
                </span>
              </template>
            </el-popconfirm>
            <el-popconfirm title="确认将该会员密码重置为默认密码？" @confirm="resetPwd(row)">
              <template #reference>
                <span class="row-action-wrap">
                  <el-tooltip content="重置密码" placement="top" :show-after="0">
                    <el-button
                      class="row-action"
                      text
                      circle
                      v-perm="'m:member:resetPwd'"
                      aria-label="重置密码"
                    ><el-icon><Key /></el-icon></el-button>
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
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { maskPhone } from '../../utils/mask'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, phone: '' })

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.phone) delete params.phone
    const page = await request.get('/member/page', { params })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

async function toggle(row) {
  await request.put(`/member/${row.id}/status`, null, { params: { status: row.status === 1 ? 0 : 1 } })
  ElMessage.success('已更新')
  load()
}

async function resetPwd(row) {
  await request.put(`/member/${row.id}/password`)
  ElMessage.success('已重置为默认密码')
}

onMounted(load)
</script>
