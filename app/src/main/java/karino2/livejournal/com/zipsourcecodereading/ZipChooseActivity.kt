package karino2.livejournal.com.zipsourcecodereading

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.EditText
import java.io.File
import java.io.IOException


class ZipChooseActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_PICK_ZIP = 1
        const val FOLDER_NAME = "ZipSourceCodeReading"

        @Throws(IOException::class)
        fun ensureDirExist(dir: File) {
            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    throw IOException()
                }
            }
        }

        @Throws(IOException::class)
        fun getStoreDirectory(): File {
            val dir = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
            ensureDirExist(dir)
            return dir
        }

        @Throws(IOException::class)
        fun getTempDirectory(): File {
            val dir = File(getStoreDirectory(), "tmp")
            ensureDirExist(dir)
            return dir
        }

        fun findIndex(zipPath: File) : File? {
            val cand1 = indexCandidate(zipPath)
            if(cand1.exists())
                return cand1
            val cand2 = File(zipPath.absolutePath + ".idx")
            if(cand2.exists())
                return cand2
            return null
        }

        fun indexCandidate(zipPath: File): File {
            return File(getStoreDirectory(), zipPath.name + ".idx")
        }

    }

    private val zipPathField: EditText
        get() {
            val et = findViewById(R.id.zipPathField) as EditText
            return et
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zip_choose)

        val zipPath = MainActivity.lastZipPath(this)
        zipPath?.let{ zipPathField.setText(zipPath) }

        findViewById(R.id.browseZipButton).setOnClickListener { _ ->
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setType("application/zip")
            startActivityForResult(intent, REQUEST_PICK_ZIP);
        }

        findViewById(R.id.indexStartButton).setOnClickListener { _ ->
            val path = zipPathField.text.toString()
            onZipPathChosen(path)
        }

    }

    private fun onZipPathChosen(path: String) {
        MainActivity.writeLastZipPath(this, path)
        val zipFile = File(path)
        val indexFile = findIndex(zipFile)
        indexFile?.let {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        startIndexingService(zipFile)

    }

    private fun startIndexingService(zipFile: File) {
        findViewById(R.id.indexStartButton).isEnabled = false
        showMessage("Start indexing...")

        val intent = Intent(this, IndexingService::class.java)

        intent.putExtra("ZIP_PATH", zipFile.absolutePath)
        startService(intent)
    }

    fun showMessage(msg : String) = MainActivity.showMessage(this, msg)


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode) {
            REQUEST_PICK_ZIP ->{
                if(resultCode == RESULT_OK) {
                    data?.getData()?.getPath()?.let { zipPathField.setText(it) }
                }
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
