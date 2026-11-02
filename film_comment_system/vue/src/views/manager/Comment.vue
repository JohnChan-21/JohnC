<template>
  <div>
    <div class="card" style="margin-bottom: 5px">
      <el-input v-model="data.filmName" placeholder="请输入电影名称查询" style="width: 300px; margin-right: 10px"></el-input>
      <el-button type="primary" @click="load">查询</el-button>
      <el-button type="info" @click="reset">重置</el-button>
    </div>
    <div class="card" style="margin-bottom: 5px">

      <el-table :data="data.tableData" stripe>
        <el-table-column prop="filmName" label="电影名称" />
        <el-table-column prop="score" label="评分">
          <template #default="scope">
            <el-rate disabled v-model="scope.row.score" allow-half />
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="评论">
          <template #default="scope">
            <el-button @click="preview(scope.row.comment)">查看内容</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="issuerName" label="评论人名称" />
        <el-table-column prop="time" label="评论时间" />
        <el-table-column prop="type" label="类型">
          <template #default="scope">
            <el-tag type="success" v-if="scope.row.type === '短评'">短评</el-tag>
            <el-tag type="success" v-if="scope.row.type === '长评'">长评</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160">
          <template #default="scope">
            <el-button v-if="canEdit(scope.row)" 
                        type="primary" 
                        @click="handleEdit(scope.row)">编辑</el-button>
            <el-button v-if="canDelete(scope.row)" 
                        type="danger" 
                        @click="del(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="card">
      <el-pagination background
                     layout="total, prev, pager, next"
                     v-model:current-page="data.pageNum"
                     v-model:page-size="data.pageSize"
                     :total="data.total"
                     @current-change="load"
      />
    </div>

    <el-dialog v-model="data.formVisibleComment" title="评论内容" width="40%">
      <div v-html="data.commentContent"></div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="data.formVisibleComment = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="data.formVisible" title="评论信息" width="50%">
      <el-form :model="data.form" label-width="80px" style="padding-right: 20px">
        <el-form-item label="打分">
          <el-rate v-model="data.form.score" allow-half show-score></el-rate>
        </el-form-item>
        <el-form-item label="内容" v-if="data.form.type === '短评'">
          <el-input :rows="5" type="textarea" v-model="data.form.comment" autocomplete="off" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="内容" prop="comment" v-if="data.form.type === '长评'">
          <div style="border: 1px solid #ccc; width: 100%">
            <Toolbar
                style="border-bottom: 1px solid #ccc"
                :editor="editorRef"
                :mode="mode"
            />
            <Editor
                style="height: 500px; overflow-y: hidden;"
                v-model="data.form.comment"
                :mode="mode"
                :defaultConfig="editorConfig"
                @onCreated="handleCreated"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="save">保 存</el-button>
          <el-button @click="data.formVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { reactive, onBeforeUnmount, shallowRef } from "vue";
import request from "@/utils/request";
import { ElMessage, ElMessageBox } from "element-plus";
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'

const data = reactive({
  user: JSON.parse(localStorage.getItem('system-user') || '{}'),
  tableData: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
  filmName: null,
  formVisible: false,
  form: {},
  formVisibleComment: false,
  commentContent: null
})

/* wangEditor5 初始化：编辑长评时用富文本编辑器 */
const baseUrl = import.meta.env.VITE_BASE_URL
const editorRef = shallowRef()  // 编辑器实例必须用 shallowRef，被深度响应式代理会卡死
const mode = 'default'
const editorConfig = { MENU_CONF: {} }
// 图片上传配置
editorConfig.MENU_CONF['uploadImage'] = {
  server: baseUrl + '/files/upload',  // 服务端图片上传接口
  fieldName: 'file'  // 服务端图片上传接口参数
}
// 组件销毁时及时销毁编辑器，否则可能造成内存泄漏
onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor == null) return
  editor.destroy()
})
// 记录 editor 实例
const handleCreated = (editor) => {
  editorRef.value = editor
}

// 预览评论内容
const preview = (comment) => {
  data.commentContent = comment
  data.formVisibleComment = true
}


const load = () => {
  request.get('/comment/selectPage', {
    params: {
      pageNum: data.pageNum,
      pageSize: data.pageSize,
      filmName: data.filmName
    }
  }).then(res => {
    data.tableData = res.data?.list || []
    data.total = res.data?.total || 0
  })
}
load()

// 重置查询条件
const reset = () => {
  data.filmName = null
  load()
}

// 初始化新增的数据
const handleAdd = () => {
  data.form = {}
  data.formVisible = true
}

// 新增评论
const add = () => {
  request.post('/comment/add', data.form).then(res => {
    if (res.code === 200) {
      load()
      data.formVisible = false
      ElMessage.success('操作成功')
    } else {
      ElMessage.error(res.msg)
    }
  })
}

// 编辑按钮触发
const handleEdit = (row) => {
  data.form = JSON.parse(JSON.stringify(row))  // 给表单赋值当前的行对象的数据  深度拷贝
  data.formVisible = true
}

// 更新评论
const update = () => {
  request.put('/comment/update', data.form).then(res => {
    if (res.code === 200) {
      load()
      data.formVisible = false
      ElMessage.success('操作成功')
    } else {
      ElMessage.error(res.msg)
    }
  })
}

// 保存按钮触发
const save = () => {
  data.form.id ? update() : add()
}

// 删除评论
const del = (id) => {
  ElMessageBox.confirm('删除数据后无法恢复，您确认吗？', '确认删除', { type: 'warning' }).then(res => {
    request.delete('/comment/delete/' + id).then(res => {
      if (res.code === 200) {
        load()
        ElMessage.success('操作成功')
      } else {
        ElMessage.error(res.msg)
      }
    })
  }).catch(err => {})
}

// 是否可编辑该评论：管理员可编辑任意，普通用户仅可编辑本人（Number 归一，防字符串/数字不一致）
const canEdit = (row) => {
  if (data.user.role === 'ADMIN') return true
  return Number(row.issuerId) === Number(data.user.id)
}

// 是否可删除该评论：管理员可删任意，普通用户仅可删本人（Number 归一，防字符串/数字不一致）
const canDelete = (row) => {
  if (data.user.role === 'ADMIN') return true
  return Number(row.issuerId) === Number(data.user.id)
}

</script>