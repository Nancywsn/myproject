package github.leavesczy.wifip2p.models

import java.io.File

/**
 * @Author: CZY
 * @Date: 2022/9/26 17:49
 * @Desc:
 */
//用密封类表示socket发送的消息类型
sealed class ViewState {

    object Idle : ViewState()

    object Connecting : ViewState()

    object Receiving : ViewState()

    class Success(val file: File) : ViewState()

    class Failed(val throwable: Throwable) : ViewState()

}