package jp.hay.bon2nmea

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment

class SimpleAlertDialog: DialogFragment() {
    private lateinit var message: String

    companion object {
        private const val MESSAGE1 = "MESSAGE1"

        @JvmStatic
        fun Create(message: String): SimpleAlertDialog {
            val fragment = SimpleAlertDialog()
            val args = Bundle()
            args.putString(MESSAGE1, message)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        message = requireArguments().getString(MESSAGE1,"")
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("エラー")
            .setMessage(message)
            .setPositiveButton("戻る") { dialog, id ->
                Log.v(null, "dialog:$dialog which:$id")
            }

        return builder.create()
    }
}