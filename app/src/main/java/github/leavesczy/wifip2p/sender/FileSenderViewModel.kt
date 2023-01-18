package github.leavesczy.wifip2p.sender

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wifip2p.Constants
import github.leavesczy.wifip2p.models.FileTransfer
import github.leavesczy.wifip2p.models.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

/**
ViewModel的一个重要作用就是可以帮助Activity分担一部分工作，它是专门用于存放与界
面相关的数据的。也就是说，只要是界面上能看得到的数据，它的相关变量都应该存放在
ViewModel中，而不是Activity中，这样可以在一定程度上减少Activity中的逻辑。
 在Activity旋转时，ViewModel里的数据不会丢失，因为生命周期比Activity长
 */
//class view :ViewModel(){}
class FileSenderViewModel(context: Application) :
    AndroidViewModel(context) {

    //MutableSharedFlow冷数据流，可发送给订阅方消息
    private val _viewState = MutableSharedFlow<ViewState>() //ViewState是一个名称/值的对象集合，可以保存多种数据类型

    val viewState: SharedFlow<ViewState> = _viewState

    private val _log = MutableSharedFlow<String>()  //用户可见

    val log: SharedFlow<String> = _log

    private var job: Job? = null

    fun send(ipAddress: String, fileUri: Uri) {
        if (job != null) {
            return
        }
        //viewModelScope是coroutineScope函数，将外部协程挂起，只有当它作用域内的所有代码和子协程都执行完毕之后，
        // coroutineScope函数之后的代码才能得到运行。coroutineScope函数只会阻塞当前协程，既不影响其他协程，也不影响任何线程
        // 因此不会造成任何性能上的问题
        job = viewModelScope.launch {
            //不管是GlobalScope.launch函数还是launch函数，它们都会返回
            //一个Job对象，只需要调用Job对象的cancel()方法就可以取消协程
            withContext(context = Dispatchers.IO) {//withContext特殊的作用域构建器，挂起函数，async函数的一种简化版写法
                //强制要求的线程参数Dispatchers.IO表示会使用一种较高并发的线程策略
                _viewState.emit(value = ViewState.Idle)

                var socket: Socket? = null
                var outputStream: OutputStream? = null
                var objectOutputStream: ObjectOutputStream? = null
                var fileInputStream: FileInputStream? = null

                try {   //try catch 语句来捕获异常并处理
                    val cacheFile = //构建File对象指定缓存内文件路径，fileUri是内存中选定的文件路径
                        saveFileToCacheDir(context = getApplication(), fileUri = fileUri)
                    val fileTransfer = FileTransfer(fileName = cacheFile.name)  //一种文件信息模型

                    _viewState.emit(value = ViewState.Connecting)
                    _log.emit(value = "待发送的文件: $fileTransfer")
                    _log.emit(value = "开启 Socket")

                    socket = Socket()   //创建
                    socket.bind(null)   //初始化绑定

                    _log.emit(value = "socket connect，如果三十秒内未连接成功则放弃")

                    socket.connect(InetSocketAddress(ipAddress, Constants.PORT),
                        30000) //ipAddress目的接收地址，超时时间设为30秒

                    _viewState.emit(value = ViewState.Receiving)
                    _log.emit(value = "连接成功，开始传输文件")

                    outputStream = socket.getOutputStream() //socket输出流
                    //ObjectOutputStream把对象转成字节数据的输出到文件中保存，对象的输出过程称为序列化，可实现对象的持久存储。
                    objectOutputStream = ObjectOutputStream(outputStream)
                    objectOutputStream.writeObject(fileTransfer)    //  writeObject 方法用于将对象写入流中

                    fileInputStream = FileInputStream(cacheFile)    //读取硬盘上的文件，应该使用输入流
                    val buffer = ByteArray(1024 * 100)  //指定多少字节的数组，存储文件内容
                    var length: Int
                    while (true) {  //按指定大小分块输出文件内容
                        length = fileInputStream.read(buffer)   //该read方法返回的是往数组中存了多少字节，内存到缓存
                        if (length > 0) {
                            outputStream.write(buffer, 0, length)   //缓存到socket
                        } else {
                            break
                        }
                        _log.emit(value = "正在传输文件，length : $length")
                    }
                    _log.emit(value = "文件发送成功")
                    _viewState.emit(value = ViewState.Success(file = cacheFile))
                } catch (e: Throwable) {
                    e.printStackTrace() //指出异常的类型、性质、栈层次及出现在程序中的位置
                    _log.emit(value = "异常: " + e.message)   //getMessage() 方法：输出错误的性质。
                    _viewState.emit(value = ViewState.Failed(throwable = e))
                } finally { //无论是否发生异常（除特殊情况外），finally 语句块中的代码都会被执行，一般用于清理资源
                    fileInputStream?.close()    //关闭流
                    outputStream?.close()
                    objectOutputStream?.close()
                    socket?.close()
                }
            }
        }
        job?.invokeOnCompletion {   //用于监听其完成或者其取消状态
            job = null
        }
    }

    private suspend fun saveFileToCacheDir(context: Context, fileUri: Uri): File {  //返回一个file对象
        return withContext(context = Dispatchers.IO) {
            val documentFile = DocumentFile.fromSingleUri(context, fileUri) //创建DocumentFile代表在给定的单一文件Uri
                ?: throw NullPointerException("fileName for given input Uri is null")
            val fileName = documentFile.name
            val outputFile = File(  //创建file对象，Random.nextInt生成1到200内的随机数
                context.cacheDir, Random.nextInt(1, 200).toString() + "_" + fileName)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()  //创建缓存文件
            val outputFileUri = Uri.fromFile(outputFile)
            copyFile(context, fileUri, outputFileUri)   //将本地文件复制到缓存
            return@withContext outputFile
        }
    }

    private suspend fun copyFile(context: Context, inputUri: Uri, outputUri: Uri) {
        //withContext函数通过Dispatchers切换到指定的线程，并在闭包内的逻辑执行结束之后，自动把线程切回去继续执行，返回最后一行的值
        withContext(context = Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw NullPointerException("InputStream for given input Uri is null")
            val outputStream = FileOutputStream(outputUri.toFile())
            val buffer = ByteArray(1024)
            var length: Int
            while (true) {
                length = inputStream.read(buffer)
                if (length > 0) {
                    outputStream.write(buffer, 0, length)
                } else {
                    break
                }
            }
            inputStream.close()
            outputStream.close()
        }
    }

}