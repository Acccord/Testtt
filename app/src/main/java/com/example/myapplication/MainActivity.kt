package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.module.interaction.ModuleConnector
import com.module.interaction.RXTXListener
import com.nativec.tools.ModuleManager
import com.rfid.RFIDReaderHelper
import com.rfid.ReaderConnector
import com.rfid.rxobserver.RXObserver
import com.rfid.rxobserver.ReaderSetting
import kotlinx.android.synthetic.main.activity_main.*

/**
 * 备注：不能在没有释放上电掉电控制设备的情况下在其他应用中执行上电掉电操作
 */
class MainActivity : AppCompatActivity() {

    var mConnector: ModuleConnector? = null

    var mReaderHelper: RFIDReaderHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBtnStart.setOnClickListener {
            ModuleManager.newInstance().setUHFStatus(true)//模块上电
        }

        mBtnStop.setOnClickListener {
            ModuleManager.newInstance().setUHFStatus(false)//模块掉电，应用结束时建议掉电
        }

        mBtnConn.setOnClickListener {
            mConnector = ReaderConnector() //构建连接器
            val bol = mConnector!!.connectCom("dev/ttyS4", 115200);//连接指定串口，返回true表示成功，false失败
            if (bol) {
                Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show()
            }
        }

        //3获取RFIDReaderHelper对象，该对象是与RFID模块交互的核心类，
        // 可以发送指令到读写器还可以通过注册观察者RXObserver对象监听读写器返回数据
        mReaderHelper = RFIDReaderHelper.getDefaultHelper() //获取
        mReaderHelper?.realTimeInventory(
            0xFF.toByte(),
            0x01.toByte()
        ) //发送实时盘存指令，更多指令参考API文档，返回的数据在RXObserver 相应的方法中回调。
        mReaderHelper?.registerObserver(rxObserver)
        mReaderHelper?.setRXTXListener(listener)

    }

    //4.获取RFID模块的数据返回，继承RXObserver类覆盖相应的的方法，
    // 通过RFIDReaderHelper的registerObserver方法注册到RFIDReaderHelper中，
    // 后台线程在读取到RFID模块返回的相应数据的时候会回调对应的方法，作为参数传递出来。
    // 因此RXObserver中的各种回调方法运行在子线程中。你没必有覆盖所有的方法，只需覆盖你用到的方法即可。
    val rxObserver = object : RXObserver() {

        //如果指令没有返回额外数据仅包含命令执行的状态码（例如RFIDReaderHelper中的各种以set开头的设置指令函数，）会回调该方法
        //如果指令返回数据异常一定会回调该方法 status 为异常码
        //cmd可以用来区分具体是哪条命令的返回，命令参考CMD类文档，status指令执行状态码，参考ERROR类文档
        override fun onExeCMDStatus(cmd: Byte, status: Byte) {
            super.onExeCMDStatus(cmd, status)
            Log.e("Vii", "onExeCMDStatus")
        }

        //当发送查询读写器设置指令（例如RFIDReaderHelper中的各种以get开头的查询指令函数）会回调该方法，若有返回值会存储在readerSetting相应字段中
        //具体可以参考API文档中ReaderSetting 各个字段的含义
        override fun refreshSetting(readerSetting: ReaderSetting?) {
            super.refreshSetting(readerSetting)
            Log.e("Vii", "refreshSetting")
        }

        //通过函数getInventoryBufferTagCount 得到缓存中盘存标签的数量，数据是通过inventory盘存到读写器缓存区中标签数量，无重复标签的数量
        override fun onGetInventoryBufferTagCount(nTagCount: Int) {
            super.onGetInventoryBufferTagCount(nTagCount)
            Log.e("Vii", "nTagCount = $nTagCount")
        }
    }

    //6.高级
    //(1).监听发送和接收数据，以及模块的链接状态。
    // 实现RXTXListener接口将其设置到RFIDReaderHelper类中
    val listener = object : RXTXListener {
        override fun reciveData(p0: ByteArray?) {
            ////获取从RFID模块接收到的数据
            val data = String(p0!!)
            Log.e("Vii", "reciveData = $data")
        }

        override fun onLostConnect() {
            //链接断开会回调该方法。
            Log.e("Vii", "onLostConnect")
        }

        override fun sendData(p0: ByteArray?) {
            //获取发送到RFID模块的数据
            val data = String(p0!!)
            Log.e("Vii", "sendData = $data")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        release()
    }

    /**
     * 5.释放资源
     * 退出应用的时候一定要释放相应的资源 示例代码：
     */
    private fun release() {
        //移除指定RXObserver监听
        mReaderHelper?.unRegisterObserver(rxObserver);
        //停止相应的线程，关闭相应I/O资源，调用该方法后RFIDReaderHelper 的各种方法不能用，否则//会报异常，
        //同时数据也不能同时接收。必须重新调用//connector.connectCom("dev/ttyS4",115200); RFIDReaderHelper才能正常工作
        mConnector?.disConnect()
        //释放模块上电掉电控制设备，退出应用的时候必须调用该方法，
        ModuleManager.newInstance().release()
    }
}
