package github.leavesczy.wifip2p

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import github.leavesczy.wifip2p.receiver.FileReceiverActivity
import github.leavesczy.wifip2p.sender.FileSenderActivity
import kotlinx.android.synthetic.main.activity_main.*

/**
1.在 AndroidManifest 中声明相关权限（网络和文件读写权限）

2.获取 WifiP2pManager ，注册相关广播监听Wifi直连的状态变化

3.指定某一台设备为服务器（用来接收文件），创建群组并作为群主存在，在指定端口监听客户端的连接请求，等待客户端发起连接请求以及文件传输请求

4.客户端（用来发送文件）主动搜索附近的设备，加入到服务器创建的群组，获取服务器的IP地址，向其发起文件传输请求

 */
class MainActivity : BaseActivity() {

    private val requestedPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLaunch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { it ->
        if (it.all { it.value }) {
            showToast("已获得全部权限")
        } else {
            onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnCheckPermission.setOnClickListener {
            requestPermissionLaunch.launch(requestedPermissions)
        }
        btnSender.setOnClickListener {
            if (allPermissionGranted()) {
                startActivity(FileSenderActivity::class.java)
            } else {
                onPermissionDenied()
            }
        }
        btnReceiver.setOnClickListener {
            if (allPermissionGranted()) {
                startActivity(FileReceiverActivity::class.java)
            } else {
                onPermissionDenied()
            }
        }
    }

    private fun onPermissionDenied() {
        showToast("缺少权限，请先授予权限")
    }

    private fun allPermissionGranted(): Boolean {
        requestedPermissions.forEach {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

}