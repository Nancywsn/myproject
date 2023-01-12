package github.leavesczy.wifip2p

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * @Author: leavesCZY
 * @Desc:
 */
open class BaseActivity : AppCompatActivity() {

    private var loadingDialog: ProgressDialog? = null   //"?"加在变量名后，系统在任何情况不会报它的空指针异常

    //显示进度条
    protected fun showLoadingDialog(message: String = "", cancelable: Boolean = true) {
        loadingDialog?.dismiss()   //安全调用符，取消进度条对话框
        loadingDialog = ProgressDialog(this).apply {
            setMessage(message)     // 设置消息内容
            setCancelable(cancelable)   //设置是否可以通过点击Back键取消
            setCanceledOnTouchOutside(false)    // 设置在点击Dialog外是否取消Dialog进度条
            show()
        }
    }

    //取消进度条
    protected fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }

    //定义父类方法，全局可用
    protected fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    //开启Activity
    protected fun <T : Activity> startActivity(clazz: Class<T>) {
        startActivity(Intent(this, clazz))
    }

}