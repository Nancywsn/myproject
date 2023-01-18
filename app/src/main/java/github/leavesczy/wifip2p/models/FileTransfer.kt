package github.leavesczy.wifip2p.models

import java.io.Serializable

/**
 * @Author: CZY
 * @Date: 2022/9/26 17:10
 * @Desc:
 */
//数据类用于表示文件内容
data class FileTransfer(val fileName: String) : Serializable