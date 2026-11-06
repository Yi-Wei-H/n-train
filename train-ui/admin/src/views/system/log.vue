<template>
  <div class="page">
    <el-card class="search-card" shadow="never">
      <el-tabs v-model="tab" class="page-tabs" @tab-change="load">
        <el-tab-pane label="登录日志" name="login" />
        <el-tab-pane label="操作日志" name="oper" />
      </el-tabs>
      <el-form inline>
        <el-form-item label="账号">
          <el-input v-model="query.username" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table v-if="tab === 'login'" :data="rows" v-loading="loading">
        <el-table-column prop="username" label="账号" width="150" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ row.userType === 'A' ? '管理员' : '用户' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="150" class-name="mono" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="msg" label="消息" min-width="180" show-overflow-tooltip />
        <el-table-column prop="loginAt" label="时间" width="180" class-name="mono" />
      </el-table>

      <el-table v-else :data="rows" v-loading="loading">
        <el-table-column prop="username" label="操作人" width="150" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="operation" label="动作" width="120" />
        <el-table-column prop="method" label="方法" min-width="200" show-overflow-tooltip class-name="mono" />
        <el-table-column prop="resultCode" label="响应码" width="90" align="right" class-name="mono" />
        <el-table-column prop="costMs" label="耗时ms" width="90" align="right" class-name="mono" />
        <el-table-column prop="ip" label="IP" width="150" class-name="mono" />
        <el-table-column prop="operAt" label="时间" width="180" class-name="mono" />
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
import request from '../../utils/request'

const tab = ref('login')
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ pageNum: 1, pageSize: 10, username: '' })

async function load() {
  loading.value = true
  try {
    const url = tab.value === 'login' ? '/system/log/login/page' : '/system/log/oper/page'
    const page = await request.get(url, { params: query })
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
