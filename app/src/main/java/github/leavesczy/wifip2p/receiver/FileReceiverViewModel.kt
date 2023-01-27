package github.leavesczy.wifip2p.receiver

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wifip2p.Constants
import github.leavesczy.wifip2p.models.FileTransfer
import github.leavesczy.wifip2p.models.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

class FileReceiverViewModel(context: Application) :
    AndroidViewModel(context) {

    private val _viewState = MutableSharedFlow<ViewState>()

    val viewState: SharedFlow<ViewState> = _viewState

    private val _log = MutableSharedFlow<String>()

    val log: SharedFlow<String> = _log

    private var job: Job? = null

    //3.2
    //使用 IntentService 在后台监听客户端的 Socket 连接请求，并通过输入输出流来传输文件。
    //此处的代码比较简单，就只是在指定端口一直堵塞监听客户端的连接请求，获取待传输的文件信息模型 FileTransfer ，之后就进行实际的数据传输
    fun startListener() {
        if (job != null) {
            return
        }
        job = viewModelScope.launch(context = Dispatchers.IO) {
            _viewState.emit(value = ViewState.Idle)

            var serverSocket: ServerSocket? = null
            var clientInputStream: InputStream? = null
            var objectInputStream: ObjectInputStream? = null
            var fileOutputStream: FileOutputStream? = null
            try {
                _viewState.emit(value = ViewState.Connecting)
                log(log = "开启 Socket")

                serverSocket = ServerSocket()   //创建服务端socket
                serverSocket.bind(InetSocketAddress(Constants.PORT))    //绑定端口号
                serverSocket.reuseAddress = true
                serverSocket.soTimeout = 30000  //超时设为30秒

                log(log = "socket accept，三十秒内如果未成功则断开链接")

                val client = serverSocket.accept()  //监听客户端请求

                _viewState.emit(value = ViewState.Receiving)

                clientInputStream = client.getInputStream()
                objectInputStream = ObjectInputStream(clientInputStream)

                val fileTransfer = objectInputStream.readObject() as FileTransfer
                val file = File(getCacheDir(context = getApplication()), fileTransfer.fileName) //创建缓存中的file对象

                log(log = "连接成功，待接收的文件: $fileTransfer")
                log(log = "文件将保存到: $file")
                log(log = "开始传输文件")

                fileOutputStream = FileOutputStream(file)   //FileOutputStream流用来写入数据到File对象表示的文件
                val buffer = ByteArray(1024 * 100)
                while (true) {
                    val length = clientInputStream.read(buffer) //socket到缓存
                    if (length > 0) {
                        fileOutputStream.write(buffer, 0, length)   //缓存到内存
                    } else {
                        break
                    }
                    log(log = "正在传输文件，length : $length")
                }
                _viewState.emit(value = ViewState.Success(file = file))
                log(log = "文件接收成功")
            } catch (e: Throwable) {
                log(log = "异常: " + e.message)
                _viewState.emit(value = ViewState.Failed(throwable = e))
            } finally {
                serverSocket?.close()
                clientInputStream?.close()
                objectInputStream?.close()
                fileOutputStream?.close()
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
    }

    private fun getCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, "FileTransfer")   //创建面向缓存的file对象
        cacheDir.mkdirs()   //创建文件
        return cacheDir
    }

    private suspend fun log(log: String) {
        _log.emit(value = log)
    }

}