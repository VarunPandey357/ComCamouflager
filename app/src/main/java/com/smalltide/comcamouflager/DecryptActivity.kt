package com.smalltide.comcamouflager

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.smalltide.comcamouflager.databinding.ActivityDecryptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class DecryptActivity : AppCompatActivity() {

    private var binding:ActivityDecryptBinding ?= null

    var customProgressDialog: Dialog? = null

    private var decode_str:String=""
    private var btm: Bitmap?=null
    private var final_str:String?=""
    private  var builder: AlertDialog.Builder?=null
    private val valid_imgornot="011010010110111001100110011010010110111001101001"

    //valid variable to check whether data present in image or not
    private var valid:Int=1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecryptBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        builder = AlertDialog.Builder(this)

        binding?.toolbar?.setNavigationIcon(R.drawable.arrow_icon)
        binding?.toolbar?.setTitle(R.string.app_name)
        binding?.toolbar?.setNavigationOnClickListener() {
            finish()
        }

        //Getting image from gallery
        val loadImage = registerForActivityResult(
            ActivityResultContracts.GetContent(),
            ActivityResultCallback{
                binding?.tvAddImgToDecrypt?.text = "Change Image to Decrypt"
                try {
                    it?.let {
                        if(Build.VERSION.SDK_INT < 28) {
                            btm = MediaStore.Images.Media.getBitmap(
                                this.contentResolver,
                                it
                            )
                        } else {
                            val source = ImageDecoder.createSource(this.contentResolver, it)
                            btm = ImageDecoder.decodeBitmap(source,
                                ImageDecoder.OnHeaderDecodedListener { decoder, info, source ->
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                decoder.isMutableRequired = true
                            })

                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            })
        binding?.tvAddImgToDecrypt?.setOnClickListener {
            loadImage.launch("image/*")
        }

        //Decryption of data from image
        binding?.btnDecrypt?.setOnClickListener {
            if(binding?.etDecryptKey?.text.toString().length==16) {
                if (btm != null) {
                    Log.e("111", "its first")
                    showProgressDialog()
                    lifecycleScope.launch{
                        decrypt()
                        //Log.e("afterdecryptttfinalstr",final_str.toString())

                        if(final_str.isNullOrBlank())
                            Toast.makeText(this@DecryptActivity,"No data encoded in image",Toast.LENGTH_LONG).show()
                        else {
                            binding?.tvDecryptedTxt?.text = final_str
                            binding?.tvDecryptedTxt?.visibility = View.VISIBLE
                        }
                        //reseting the variables for next use
                        decode_str = ""
                        final_str=""
                        valid=1
                    }
                    cancelProgressDialog()
                }else{
                    Toast.makeText(this, "Image not added", Toast.LENGTH_SHORT).show()
                }
            }else{
                val k=binding?.etDecryptKey?.text.toString().length
                Toast.makeText(this, "Key length must be 16 digits not $k ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@DecryptActivity)
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

    private suspend fun decrypt(){
        withContext(Dispatchers.IO){

            exctract_str_from_img(btm!!)
            Log.e("padddinf", valid.toString())
            if (valid == 1) {

                //converting decode_string to base64 for decrypting
                val base64_str = convo_to_base64(decode_str)

                //decrypting the string
                final_str = decod_str(base64_str)

                Log.e("finalllll",final_str.toString())

            }
            else {
                    Log.e("ccchhheeecckkk","No data encoded in image")
            }
        }
    }

    private suspend fun exctract_str_from_img(btm: Bitmap){
        val w: Int = btm.getWidth()
        val h: Int = btm.getHeight()
        var data = IntArray(w * h)
        //getting btm piixel array
        btm.getPixels(data, 0, w, 0, 0, w, h)
        Log.e("w", w.toString())
        Log.e("h", h.toString())

        var count = 0
        var chk = 1
        //modifying pixel data by encoding string(to encode)
        Log.e("r after", (data.get(1) shr 8 and 0xff).toString())
        Log.e("r2 afte", (data.get(2) shr 8 and 0xff).toString())

        for (y in 0 until h) {
            if ((chk == 1) and (valid==1) ){

                for (x in 0 until w) {
                    val index: Int = y * w + x
                    val R: Int = data.get(index) shr 16 and 0xff //bitwise shifting
                    val G: Int = data.get(index) shr 8 and 0xff
                    val B: Int = data.get(index) and 0xff

                    if (termi(count) or (valid==0)) {
                        chk = 0
                        break
                    } else {
                        termi_decod(R, count)
                        count++
                    }


                    if (termi(count) or (valid==0)) {

                        chk = 0
                        break
                    }
                    else {
                        termi_decod(G, count)
                        count++
                    }

                    if (termi(count) or (valid==0)) {
                        chk = 0
                        break
                    } else {
                        termi_decod(B, count)
                        count++
                    }
                    //Log.e("count3pair",count.toString())
                    //to restore the values after RGB modification, use
                    //next statement
                    data[index] = -0x1000000 or (R shl 16) or (G shl 8) or B
                }
            } else {
                break
            }
        }

    }

    //decoding rgb pixel value and adding lsb to str
    private suspend fun termi_decod(co: Int, count: Int) {
        val b = Integer.toBinaryString(co)
        if (count<2000){//Log.e("binart", b)
        }
        decode_str = decode_str + b[b.length - 1]
        if (decode_str!!.length==48 ){if (decode_str!=valid_imgornot){valid=0}}
        if(decode_str.length%8==0) {
            val toint = Integer.parseInt(
                decode_str.slice(decode_str.length - 8..decode_str.length - 1),
                2
            )
            val tochr = toint.toChar()
            if(count<2000)
            {Log.e("chr", tochr.toString())
            }
        }
    }

    //to check terminating symbol
    private suspend fun termi(count: Int):Boolean{
        if (decode_str.length>=16){
            val termi1=decode_str.slice(decode_str.length - 16..decode_str.length - 9)
            val termi1_int=Integer.parseInt(termi1, 2)
            val termi2=decode_str.slice(decode_str.length - 8..decode_str.length - 1)
            val termi2_int=Integer.parseInt(termi2, 2)
            if((termi1_int==23) and (termi2_int==30)){
                decode_str=decode_str.slice(48..decode_str.length - 16)

                return(true)
            }
        }
        //Log.e("termi_bool",count.toString())
        return false
    }

    private suspend fun convo_to_base64(s: String):String{
        var base64_str:String=""
        for(i in 0..s.length-8 step  8){
            val bin=s.slice(i..i + 7)
            val toint=Integer.parseInt(bin, 2)
            val tochr=toint.toChar()
            base64_str += tochr
        }
        return base64_str
    }

    private suspend fun decod_str(bs: String):String?{
        val dkey: Key = SecretKeySpec(binding?.etDecryptKey?.text.toString().toByteArray(), "AES")
        val cc: Cipher = Cipher.getInstance("AES")
        cc.init(Cipher.DECRYPT_MODE, dkey)
        var ree:ByteArray?=null
        try {
            ree = cc.doFinal(Base64.decode(bs, Base64.NO_WRAP or Base64.NO_PADDING))
        }
        catch (e: Exception){
            runOnUiThread { Toast.makeText(this, "Wrong security Key", Toast.LENGTH_SHORT).show() }
        }
        val st: String? = ree?.let { String(it) }
        return st
    }
}