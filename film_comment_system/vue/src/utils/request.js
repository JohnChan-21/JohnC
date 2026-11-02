import { ElMessage } from 'element-plus'
import router from '../router'
import axios from "axios";

const request = axios.create({
    baseURL: import.meta.env.VITE_BASE_URL,
    timeout: 30000,  // 后台接口超时时间设置
    withCredentials: true  // 关键：允许跨域携带 Cookie（Sa-Token 通过 Cookie 传输）
})

// request 拦截器
// 可以自请求发送前对请求做一些处理
request.interceptors.request.use(config => {
    config.headers['Content-Type'] = 'application/json;charset=utf-8';
    // 鉴权仅依赖 http-only Cookie（withCredentials 已开启），前端不再持有 token
    return config
}, error => {
    return Promise.reject(error)
});


let isRelogin = false;
function handleUnauth(msg){
    if(isRelogin) return;
    isRelogin = true;
    ElMessage.error(msg || '登录已过期，请重新登录');
    localStorage.removeItem('system-user');
    router.push('/login').finally(()=>{
        isRelogin = false
    })
}
// response 拦截器
// 可以在接口响应后统一处理结果
request.interceptors.response.use(
    response => {
        let res = response.data;
        // 如果是返回的文件
        if (response.config.responseType === 'blob') {
            return res
        }
        // 兼容服务端返回的字符串数据
        if (typeof res === 'string') {
            res = res ? JSON.parse(res) : res
        }
        // 当权限验证不通过的时候给出提示（Sa-Token 未登录返回 401）
        if (res.code === 401) {
            handleUnauth(res.msg)
            return Promise.reject(res)
        }
        return res;
    },
    error => {
        // 处理 HTTP 状态码 401（Sa-Token 拦截器返回）
        if (error.response && error.response.status === 401) {
            handleUnauth(error.response.data.msg)
        } else {
            console.log('err' + error)
        }
        return Promise.reject(error)
    }
)

export default request
