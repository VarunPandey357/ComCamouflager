package com.smalltide.comcamouflager

import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smalltide.comcamouflager.databinding.ActivityEncryptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


class EncryptActivity : AppCompatActivity() {

    private var binding:ActivityEncryptBinding ?= null

    private var b_string:String?=""
    private  var btm: Bitmap?=null
    private  var builder: AlertDialog.Builder?=null

    var customProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEncryptBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        //builder = AlertDialog.Builder(this)

        binding?.toolbar!!.setNavigationIcon(R.drawable.arrow_icon)
        binding?.toolbar!!.setTitle(R.string.app_name)
        binding?.toolbar!!.setNavigationOnClickListener(){
            finish()

        }


        //Getting image in which data will be encoded
        val loadImage = registerForActivityResult(
            ActivityResultContracts.GetContent(),
            ActivityResultCallback{
                binding?.ivSelectedEncrypt?.setImageURI(it)
                try {
                    it?.let {
                        if(Build.VERSION.SDK_INT < 28) {
                            btm = MediaStore.Images.Media.getBitmap(
                                this.contentResolver,
                                it
                            )
                        } else {
                            val source = ImageDecoder.createSource(this.contentResolver, it)
                            btm = ImageDecoder.decodeBitmap(source,ImageDecoder.OnHeaderDecodedListener { decoder, info, source ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                decoder.isMutableRequired = true
                            })

                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            })
        binding?.tvAddImgEncrypt?.setOnClickListener {
            loadImage.launch("image/*")
        }

        //getting bitmap
        //val drawable = binding?.ivSelectedEncrypt?.drawable as BitmapDrawable
        //val flImage:ImageView = findViewById(R.id.ivSelectedEncrypt)
        //btm = getBitmapFromView(flImage)                  //!! can cause error

        //Encryption of data and hiding it in image
        binding?.btnEncrypt?.setOnClickListener() {

            if(binding?.etEncryptKey?.text.toString().length==16) {
                if(binding?.etEncodeTxt?.text.toString().length>0) {
                    if (btm != null) {

                        showProgressDialog()
                        lifecycleScope.launch{
                            encrypt()
                        }
                        cancelProgressDialog()
                        Toast.makeText(this,"all OKay ",Toast.LENGTH_LONG).show()
                    }
                    else{
                        Toast.makeText(this, "ADD A IMAGE FIRST", Toast.LENGTH_SHORT).show()
                    }

                }
                else{ Toast.makeText(this, "Text to hide is empty", Toast.LENGTH_SHORT).show()
                }
            }
            else{
                val k=binding?.etEncryptKey?.text.toString().length
                Toast.makeText(this, "Key must be of 16 digits not $k ", Toast.LENGTH_SHORT).show()
            }

        }

    }
    /*
    private fun getBitmapFromView(view: View) : Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bg = view.background
        bg.draw(canvas)
        view.draw(canvas)
        return returnedBitmap
    }
    */

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@EncryptActivity)
        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        customProgressDialog?.setCancelable(false)
        //Start the dialog and display it on screen.
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private suspend fun encrypt(){
        withContext(Dispatchers.IO){

            //encrypting data using AES encryption
            val s: String = encrypting_data()
            //hiding data in image and getting modified img
            Log.e("cc__b_string", b_string.toString())
            val bitmap:Bitmap=data_hiding_in_img(s, btm!!)
            //reseting the encrypted binary string for next time use
            b_string=""
            //saving stego image to gallary
            saveMediaToStorage(bitmap)
            //saveBitmapFile(bitmap)
        }
    }

    private suspend fun encrypting_data():String{
        val key = binding?.etEncryptKey?.text.toString()
        val s = binding?.etEncodeTxt?.text.toString()
        //generating key from give  key text
        val skey: Key = SecretKeySpec(key.toByteArray(), "AES")
        print(skey.toString())
        val c: Cipher = Cipher.getInstance("AES")
        c.init(Cipher.ENCRYPT_MODE, skey)
        //encrypting text string
        val re = c.doFinal(s.toByteArray())

        Log.e("re", re.toString())

        //converting encrypted string to base64
        val re_base64 = Base64.encodeToString(re, Base64.NO_WRAP or Base64.NO_PADDING)
        Log.e("re_base64", re_base64.toString())

        //converting each chr of base64 string to binary and combining it
        for(i in re_base64){
            var single_b_string=Integer.toBinaryString((i.toInt()))
            //if binary str is less than 8 bit then making it 8 bit by adding 0's
            if(single_b_string.length<8){
                for(j in 1..(8-single_b_string.length)){
                    single_b_string="0"+single_b_string
                }
            }
            //final binary string to hide in image
            b_string= b_string+ single_b_string
        }
        Log.e("barraylength", b_string.toString())
        Log.e("barray", b_string!!.length.toString())
        return b_string.toString()

    }


    private suspend fun data_hiding_in_img(s: String, btm: Bitmap):Bitmap{
        val termi_string="0001011100011110"
        val starting_string="011010010110111001100110011010010110111001101001"
        val str_to_encode =starting_string+s+ termi_string

        Log.e("ccc2", "check check")
        Log.e("ccc222", str_to_encode)

        val w: Int = btm.getWidth()
        val h: Int = btm.getHeight()
        Log.e("ccc2222", w.toString())
        val data = IntArray(w * h)
        Log.e("ccc22222", w.toString())
        //getting btm piixel array
        btm.getPixels(data, 0, w, 0, 0, w, h)
        Log.e("ccc222222222", w.toString())
        Log.e("w", w.toString())
        Log.e("h", h.toString())

        var count = 0
        var termi_count=0
        //modifying pixel data by encoding string(to encode)
        Log.e("r be", (data.get(1) shr 8 and 0xff).toString())
        Log.e("r2 before", (data.get(2) shr 8 and 0xff).toString())

        for (y in 0 until h) {
            if (count > str_to_encode!!.length - 1) {

                break
            } else {
                for (x in 0 until w) {
                    if (count > str_to_encode!!.length - 1) {
                        break

                    } else {
                        val index: Int = y * w + x
                        Log.e("data.get(index)->", data.get(index).toString())
                        var R: Int = data.get(index) shr 16 and 0xff //bitwise shifting
                        var G: Int = data.get(index) shr 8 and 0xff //0xff means 11111111
                        var B: Int = data.get(index) and 0xff
                        // val p= intArrayOf(R,G,B)
                        //barray.add(p)

                        R = encod(R, count, str_to_encode)
                        count++
                        if(count<str_to_encode!!.length){
                            G = encod(G, count, str_to_encode)
                            count++}
                        if(count<str_to_encode!!.length){
                            B = encod(B, count, str_to_encode)
                            count++}
                        // Log.e("count",count.toString())

                        //to restore the values after RGB modification, use
                        data[index] = -0x1000000 or (R shl 16) or (G shl 8) or B
                    }
                }
            }
        }
        Log.e("r after", (data.get(1) shr 8 and 0xff).toString())
        Log.e("r2 afte", (data.get(2) shr 8 and 0xff).toString())

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        //creating bitmap of modified pixel data
        bitmap.setPixels(data, 0, w, 0, 0, w, h)

        return bitmap
    }

    //encoding each bit of string(to encode) in rgb values
    private suspend fun encod(co: Int, count: Int, str_to_encode: String):Int{
        var b=Integer.toBinaryString(co)
        if(b.length<8){
            for(j in 1..(8-b.length)){
                b="0"+b
            }
        }
        b=b.slice(0..(b.length - 2)) +str_to_encode!![count]
        val d=Integer.parseInt(b, 2)
        return d
    }

    private fun saveBitmapFile(mBitmap : Bitmap){
        var result = ""
        try{
            val bytes = ByteArrayOutputStream()
            mBitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)

            val f = File(externalCacheDir?.absoluteFile.toString()
                    + File.separator + "EncryptedImg_" + System.currentTimeMillis()/1000 + ".png"
            )

            val fo = FileOutputStream(f)
            fo.write(bytes.toByteArray())
            fo.close()
        }
        catch (e: Exception){
            e.printStackTrace()
        }
    }



    private fun saveMediaToStorage(bitmap: Bitmap) {
        try {
            val filename = "${System.currentTimeMillis()}.png"
            var fos: OutputStream? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.e("gggggggggggggggg", "kkkkkkkkkkkkkkkkkkkkkkkk")
                contentResolver?.also { resolver ->
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                    val imageUri: Uri? =
                        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                    runOnUiThread { Toast.makeText(this, "Process Done!! Image saved to Internal/Pictures", Toast.LENGTH_SHORT).show() }

                }
            } else {
                Log.e("gggggggggggggggg", "jjjjjjjjjjjjjjjjjjjjjj")
                val imagesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
                runOnUiThread { Toast.makeText(this, "Process Done!! Image saved to $imagesDir", Toast.LENGTH_SHORT).show() }

            }
            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)

            }}
        catch (e: Exception) {
            e.printStackTrace();
            runOnUiThread { Toast.makeText(this, "Error!! Image not Saved", Toast.LENGTH_SHORT).show() }
        }
    }

}