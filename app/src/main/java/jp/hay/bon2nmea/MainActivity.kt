package jp.hay.bon2nmea

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedWriter

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openButton: Button = findViewById(R.id.buttonOpen)
        openButton.setOnClickListener {
            //Snackbar.make(openButton, "Open clicked.", Snackbar.LENGTH_SHORT).show()
            openFile()
        }

        val saveButton: Button = findViewById(R.id.buttonSave)
        saveButton.setOnClickListener {
            //Snackbar.make(saveButton, "Save clicked.", Snackbar.LENGTH_SHORT).show()
            saveFile()
        }
    }

    private var m_sourceFileUri: Uri? = null
    private var m_destinationUri: Uri? = null

    private var m_sourceFileName: String? = null
    private var m_destinationFileName: String? = null

    private var m_dataVersion: LocationData.DataVersion = LocationData.DataVersion.BON1;

    private fun openFile() {
        pickFile(getDefaultUri())
    }

    private fun getDefaultUri(): Uri {
        val path = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val uri = path?.toUri() as Uri
        return uri
    }

    private fun saveFile() {
        if (m_sourceFileUri == null) {
            Log.e("Main", "file not specified.")
            SimpleAlertDialog.Create(getString(R.string.error_file_not_specified))
                .show(supportFragmentManager, SimpleAlertDialog::class.simpleName)
            return
        }
        pickSaveFile(getDefaultUri(), m_destinationFileName!!)
        //convertFile(m_sourceFileUri!!)
    }

    /// 開くファイルを選択
    fun pickFile(pickerInitialUri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, R.string.open_title.toString())

            // Optionally, specify a URI for the file that should appear in the
            // system file picker when it loads.
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        resultLauncherForOpenDocument.launch(intent)
    }

    /// ファイル選択インテントの起動と結果取得
    private val resultLauncherForOpenDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.also { intent ->
                    val uri = intent.data as Uri
                    setSourceFile(uri)
                }
            }
        }

    /// 保存先を選択
    fun pickSaveFile(pickerInitialUri: Uri, filename: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/nmea"
            putExtra(Intent.EXTRA_TITLE, filename)

            // Optionally, specify a URI for the file that should appear in the
            // system file picker when it loads.
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        resultLauncherForSaveDocument.launch(intent)
    }

    /// 保存先選択インテントの起動と結果取得
    private val resultLauncherForSaveDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.also { intent ->
                    val uri = intent.data as Uri
                    setDestinationUri(uri)
                }
            }
        }

    /// 開くファイルを設定
    private fun setSourceFile(uri: Uri) {
        Log.i("Main", "source uri=${uri}")
        val filename = getContentFileName(uri) as String
        Log.i("Main", "filename=${filename}")
        val textView = findViewById<TextView>(R.id.textView)
        textView.text = filename
        val ext = filename.substringAfterLast('.', "").uppercase()
        if (!ext.startsWith("BON")) {
            Log.e("Main", "invalid file extension")
            SimpleAlertDialog.Create(getString(R.string.error_invalid_ext))
                .show(supportFragmentManager, SimpleAlertDialog::class.simpleName)
            return
        }
        if (ext == "BON4") {
            m_dataVersion = LocationData.DataVersion.BON4
        }
        m_sourceFileUri = uri
        m_sourceFileName = filename
        m_destinationFileName = filename.substringBeforeLast('.') + ".nmea"
    }

    private fun setDestinationUri(uri: Uri) {
        Log.i("Main", "destination uri=${uri}")
        m_destinationUri = uri

        // 待機演出
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = ProgressBar.VISIBLE

        GlobalScope.launch {
            convertFile(progressBar)
        }
    }

    /// コンテントURIからファイル名を得る
    private fun getContentFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    result = cursor.getString(index)
                }
            }
        }
        return result
    }

    /// ファイルを変換して保存
    private fun convertFile(progressBar: ProgressBar) {
        if (m_sourceFileUri == null ||
            m_destinationUri == null
        ) {
            return
        }

        Log.i(
            "Main",
            "convertFile:source=${m_sourceFileName}, destination=${m_destinationFileName}"
        )

        contentResolver.openInputStream(m_sourceFileUri!!)?.use { inputStream ->
            BufferedInputStream(inputStream).use { bufferedInputStream ->
                contentResolver.openOutputStream(m_destinationUri!!)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        convertFile(bufferedInputStream, writer)
                    }
                }
            }
        }

        Log.i("Main", "complete.")
        progressBar.visibility = ProgressBar.INVISIBLE

        val view: View = findViewById(android.R.id.content)
        Snackbar.make(view, R.string.message_complete, Snackbar.LENGTH_LONG).show()
    }

    private fun convertFile(inputStream: BufferedInputStream, writer: BufferedWriter) {
        val header = ByteArray(8)
        inputStream.read(header)

        var prevLocation: LocationData? = null
        val chunk = ByteArray(26)
        while (inputStream.read(chunk) >= 0) {
            val location = LocationData(chunk, prevLocation, m_dataVersion)
            prevLocation = location
            writer.write(location.getNmea())
            writer.newLine()
        }
    }

}

