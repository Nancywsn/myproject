package github.leavesczy.wifip2p.sender

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import github.leavesczy.wifip2p.*
import github.leavesczy.wifip2p.models.ViewState
import github.leavesczy.wifip2p.utils.WifiP2pUtils
import kotlinx.android.synthetic.main.activity_file_sender.*
import kotlinx.coroutines.launch


@SuppressLint("NotifyDataSetChanged")
class FileSenderActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_sender)
        initView()
        initDevice()
        initEvent()
    }

    @SuppressLint("MissingPermission")
    private fun initView() {
        supportActionBar?.title = "文件发送端"   //标题栏文字
        btnDisconnect.setOnClickListener {
            disconnect()
        }
        btnChooseFile.setOnClickListener {
            getContentLaunch.launch("image/*")  //跳转文件选择界，获取相册
            //"image/*"指定只显示图片
        }
        btnDirectDiscover.setOnClickListener {
            if (!wifiP2pEnabled) {
                showToast("需要先打开Wifi")
                return@setOnClickListener
            }
            showLoadingDialog(message = "正在搜索附近设备") //弹出对话框
            wifiP2pDeviceList.clear()   //清除旧列表
            deviceAdapter.notifyDataSetChanged()    //更新显示的设备列表

            //4.2 通过 discoverPeers 方法搜索周边设备，回调函数用于通知方法是否调用成功
            wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {  //仅会通知您发现进程已成功，但不会提供有关其发现的实际对等设备（如有）的任何信息
                    showToast("discoverPeers Success")
                    dismissLoadingDialog()
                }
                override fun onFailure(reasonCode: Int) {
                    showToast("discoverPeers Failure：$reasonCode")
                    dismissLoadingDialog()
                }
            })
            //当搜索结束后，系统就会触发 WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION 广播，
            //此时就可以调用 requestPeers 方法获取设备列表信息
        }

        //点击第几个设备，连接第几个设备
        deviceAdapter.onItemClickListener = object : OnItemClickListener {
            override fun onItemClick(position: Int) {
                val wifiP2pDevice = wifiP2pDeviceList.getOrNull(position)   //不存在则返回默认值null
                if (wifiP2pDevice != null) {
                    connect(wifiP2pDevice = wifiP2pDevice)  //连接第几个设备
                }
            }
        }

        //自定义控件的适配器，用于显示可连接的设备
        rvDeviceList.adapter = deviceAdapter
        rvDeviceList.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean {
                return false
            }
        }
    }
    //4.1 注册P2P广播，以便获取周边设备信息以及连接状态
    private fun initDevice() {
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mWifiP2pManager == null) {
            finish()
            return
        }
        wifiP2pManager = mWifiP2pManager    //获取WifiP2pManager实例
        wifiP2pChannel = mWifiP2pManager.initialize(this, mainLooper, directActionListener) //initialize注册该应用 ，此方法会返回 WifiP2pManager.Channel，用于将您的应用连接到 WLAN P2P 框架
        broadcastReceiver = DirectBroadcastReceiver(mWifiP2pManager, wifiP2pChannel, directActionListener)  //创建广播接收器实例。这样广播接收器便可通知 Activity 感兴趣的事件并进行相应更新
        registerReceiver(broadcastReceiver, DirectBroadcastReceiver.getIntentFilter())  //注册广播接收器
    }

    private fun initEvent() {
        //协程就像轻量级的线程。线程由系统调度，协程由开发者控制，且为异步执行。
        lifecycleScope.launch { //开启一个协程
            fileSenderViewModel.viewState.collect {
                when (it) {
                    ViewState.Idle -> {
                        clearLog()
                        dismissLoadingDialog()
                    }

                    ViewState.Connecting -> {
                        showLoadingDialog(message = "")
                    }

                    is ViewState.Receiving -> {
                        showLoadingDialog(message = "")
                    }

                    is ViewState.Success -> {
                        dismissLoadingDialog()
                    }

                    is ViewState.Failed -> {
                        dismissLoadingDialog()
                    }
                }
            }
        }
        lifecycleScope.launch {
            fileSenderViewModel.log.collect {
                log(it)
            }
        }
    }


    private val fileSenderViewModel by viewModels<FileSenderViewModel>()

    //跳转到另一个Activity
    private val getContentLaunch = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { imageUri ->
        if (imageUri != null) {
            val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress     //群主的IP地址
            //getHostAddress方法返回字符串形式的IP地址
            log("getContentLaunch $imageUri $ipAddress")
            if (!ipAddress.isNullOrBlank()) {   //如果ipaddress不为空，则开始发送文件
                //isNullOrBlank为空指针或者字串长度为0或者全为空格时返回true，非空串与可空串均可调用
                fileSenderViewModel.send(ipAddress = ipAddress, fileUri = imageUri)
            }
        }
    }

    private val wifiP2pDeviceList = mutableListOf<WifiP2pDevice>()  //MutableList接口的初始化，一个接口和通用的元素集合

    private val deviceAdapter = DeviceAdapter(wifiP2pDeviceList)    //

    private var broadcastReceiver: BroadcastReceiver? = null

    private lateinit var wifiP2pManager: WifiP2pManager     //对等网络管理器

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel

    private var wifiP2pInfo: WifiP2pInfo? = null

    private var wifiP2pEnabled = false

    private val directActionListener = object : DirectActionListener {  //广播接收器的实例

        override fun wifiP2pEnabled(enabled: Boolean) {
            wifiP2pEnabled = enabled
        }

        //4.7 信息最后通过 onConnectionInfoAvailable 方法传递出来，在此可以判断当前设备是否为群主，获取群组IP地址
        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            dismissLoadingDialog()
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()    //更新列表
            btnDisconnect.isEnabled = true
            btnChooseFile.isEnabled = true
            log("onConnectionInfoAvailable")
            log("onConnectionInfoAvailable groupFormed: " + wifiP2pInfo.groupFormed)    //groupFormed字段保存是否有组建立
            log("onConnectionInfoAvailable isGroupOwner: " + wifiP2pInfo.isGroupOwner)  //isGroupOwner字段判断自己是否是GO设备
            log("onConnectionInfoAvailable getHostAddress: " + wifiP2pInfo.groupOwnerAddress.hostAddress)   //groupOwnerAddress字段保存GO设备的地址信息
            val stringBuilder = StringBuilder() //StringBuilder是一个可变的字符串类
            stringBuilder.append("\n")
            stringBuilder.append("是否群主：")
            stringBuilder.append(if (wifiP2pInfo.isGroupOwner) "是群主" else "非群主")
            stringBuilder.append("\n")
            stringBuilder.append("群主IP地址：")
            stringBuilder.append(wifiP2pInfo.groupOwnerAddress.hostAddress)
            tvConnectionStatus.text = stringBuilder //显示连接状态

            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) { //如果有组建立但自己不是GO设备，即自己是GC设备
                this@FileSenderActivity.wifiP2pInfo = wifiP2pInfo   //发送端获得GO的IP地址
            }
        }
        //至此服务器端和客户端已经通过 Wifi P2P 连接在了一起，客户端也获取到了服务器端的IP地址，此时客户端就可以给服务端发送请求了
        //消息的发送操作放到 AsyncTask 中处理，将服务器端的IP地址作为参数传进来

        //取消连接
        override fun onDisconnection() {
            log("onDisconnection")
            btnDisconnect.isEnabled = false //按钮不可点击
            btnChooseFile.isEnabled = false
            wifiP2pDeviceList.clear()   //清除旧的信息
            deviceAdapter.notifyDataSetChanged() //更新列表
            tvConnectionStatus.text = null  //界面连接状态为空
            wifiP2pInfo = null
            showToast("处于非连接状态")
        }

        //显示本设备信息
        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            log("onSelfDeviceAvailable")
            log("DeviceName: " + wifiP2pDevice.deviceName)
            log("DeviceAddress: " + wifiP2pDevice.deviceAddress)
            log("Status: " + wifiP2pDevice.status)
            val log = "deviceName：" + wifiP2pDevice.deviceName + "\n" +
                    "deviceAddress：" + wifiP2pDevice.deviceAddress + "\n" +
                    "deviceStatus：" + WifiP2pUtils.getDeviceStatus(wifiP2pDevice.status)
            tvDeviceState.text = log
        }

        //4.4 刷新可用设备列表
        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            log("onPeersAvailable :" + wifiP2pDeviceList.size)
            this@FileSenderActivity.wifiP2pDeviceList.clear()   //清除旧的信息
            this@FileSenderActivity.wifiP2pDeviceList.addAll(wifiP2pDeviceList) //更新信息
            deviceAdapter.notifyDataSetChanged()    //更新列表
            dismissLoadingDialog()
        }

        override fun onChannelDisconnected() {  //自带的回调函数，不是统一定义
            log("onChannelDisconnected")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)   //取消注册广播接收器
        }
    }

    //4.5 通过点击事件选中群主（服务器端）设备，通过 connect 方法请求与之进行连接
    //4.6 此处依然无法通过函数函数来判断连接结果，需要依靠系统发出的 WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION 方法来获取到连接结果
    @SuppressLint("MissingPermission")
    private fun connect(wifiP2pDevice: WifiP2pDevice) {
        val wifiP2pConfig = WifiP2pConfig() //WifiP2pConfig对象包含要连接的设备的信息
        wifiP2pConfig.deviceAddress = wifiP2pDevice.deviceAddress
        wifiP2pConfig.wps.setup = WpsInfo.PBC
        showLoadingDialog(message = "正在连接，deviceName: " + wifiP2pDevice.deviceName)
        showToast("正在连接，deviceName: " + wifiP2pDevice.deviceName)

        wifiP2pManager.connect(wifiP2pChannel, wifiP2pConfig, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    log("connect onSuccess")
                }

                override fun onFailure(reason: Int) {
                    showToast("连接失败 $reason")
                    dismissLoadingDialog()
                }
            })
    }

    //取消连接和移除群组
    private fun disconnect() {
        wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            //断开一个connecting的连接，即断开当前状态是Invited的连接
            override fun onFailure(reasonCode: Int) {
                log("cancelConnect onFailure:$reasonCode")
            }

            override fun onSuccess() {
                log("cancelConnect onSuccess")
                tvConnectionStatus.text = null  //显示连接状态为未连接
                btnDisconnect.isEnabled = false //按钮不使能
                btnChooseFile.isEnabled = false
            }
        })
        wifiP2pManager.removeGroup(wifiP2pChannel, null)    //移除Group，断开连接
    }

    //界面显示
    private fun log(log: String) {
        tvLog.append(log)
        tvLog.append("\n\n")
    }

    //清空下界面
    private fun clearLog() {
        tvLog.text = ""
    }

}