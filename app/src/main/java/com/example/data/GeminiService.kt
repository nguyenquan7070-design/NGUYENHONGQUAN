package com.example.data

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    // Set 60-second timeouts as requested by instructions
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    /**
     * General utility to call Gemini REST API
     */
    private suspend fun callGemini(
        prompt: String,
        systemInstruction: String? = null,
        bitmap: Bitmap? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "LỖI: Chưa cấu hình GEMINI_API_KEY trong Bí mật (Secrets)/.env. Vui lòng thêm key để kích hoạt tính năng AI."
        }

        try {
            val url = "$BASE_URL?key=$apiKey"
            val jsonRequest = JSONObject()

            // 1. Create content array
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Text Part
            val textPart = JSONObject()
            textPart.put("text", prompt)
            partsArray.put(textPart)

            // Multimodal Part (if image is provided)
            if (bitmap != null) {
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", bitmap.toBase64())
                imagePart.put("inlineData", inlineData)
                partsArray.put(imagePart)
            }

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            jsonRequest.put("contents", contentsArray)

            // 2. Add System Instruction if present
            if (systemInstruction != null) {
                val sysInstObj = JSONObject()
                val sysPartsArray = JSONArray()
                val sysTextPart = JSONObject()
                sysTextPart.put("text", systemInstruction)
                sysPartsArray.put(sysTextPart)
                sysInstObj.put("parts", sysPartsArray)
                jsonRequest.put("systemInstruction", sysInstObj)
            }

            val requestBodyString = jsonRequest.toString()
            Log.d(TAG, "Request payload length: ${requestBodyString.length}")

            val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d(TAG, "Response Code: ${response.code}")

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        if (content != null) {
                            val parts = content.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).optString("text")
                            }
                        }
                    }
                    return@withContext "LỖI: Không tìm thấy nội dung phản hồi từ API."
                } else {
                    val errCode = response.code
                    return@withContext "LỖI API ($errCode): ${response.message}\nPhản hồi chi tiết: ${responseBody ?: ""}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi Gemini API: ${e.message}", e)
            return@withContext "LỖI KẾT NỐI: ${e.localizedMessage ?: "Vui lòng kiểm tra lại kết nối mạng."}"
        }
    }

    /**
     * Module 2: AI Soạn văn bản hành chính theo nhiều mẫu loại
     */
    suspend fun draftDocument(type: String, title: String, draftOutline: String): String {
        val systemPrompt = """
            Bạn là trợ lý hành chính AI PRO cao cấp của Đội Hậu cần - Quản trị, Phòng Hậu cần Công an tỉnh Đắk Lắk.
            Hãy viết văn bản hành chính theo đúng quy định và định dạng tiêu chuẩn Việt Nam.
            Yêu cầu bắt buộc:
            - Có đầy đủ quốc hiệu, tiêu ngữ (CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM / Độc lập - Tự do - Hạnh phúc).
            - Tên cơ quan ban hành: CÔNG AN TỈNH ĐẮK LẮK / PHÒNG HẬU CẦN / ĐỘI HC-QT.
            - Đúng văn phong công vụ, nghiêm túc, chặt chẽ, chính xác tuyệt đối.
            - Các thông tin tham khảo trong dàn ý nếu thiếu thì tự động điền các mốc thời gian, địa danh Đắk Lắk một cách hợp lý và chuyên nghiệp.
        """.trimIndent()

        val prompt = """
            Hãy soạn thảo một văn bản hành chính loại: [$type]
            Với tiêu đề/nội dung chính: "$title"
            Dàn ý chi tiết hoặc thông tin đi kèm:
            $draftOutline
            
            Hoàn thiện đầy đủ nội dung từ tiêu đề, căn cứ pháp lý, nội dung chính đến phần ký tên, nơi nhận theo mô hình Nghị định 30/2020/NĐ-CP.
        """.trimIndent()

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    /**
     * Module 3: AI kiểm tra thể thức văn bản theo Nghị định 30/2020/NĐ-CP và kiểm tra rà soát theo Nghị định 20/2020/NĐ-CP
     */
    suspend fun checkDecree30Format(documentText: String): String {
        val systemPrompt = """
            Bạn là chuyên gia đầu ngành thẩm định văn bản hành chính theo Nghị định 30/2020/NĐ-CP (quy định thể thức, kỹ thuật trình bày) và rà soát thủ tục pháp lý theo Nghị định 20/2020/NĐ-CP (quy định trình tự kiểm tra, xử lý, hệ thống hóa văn bản quy phạm pháp luật) thuộc lực lượng Công an nhân dân Việt Nam.

            Nhiệm vụ rà soát văn bản của bạn phải kiểm tra tỉ mỉ và đưa ra danh sách các điểm cụ thể. Với mỗi điểm phát hiện, PHẢI ghi rõ một trong các hậu tố nhãn sau ở đầu dòng để hệ thống hiển thị màu cảnh báo trực quan:
            - Nếu đạt chuẩn, ghi rõ: "[OK] [Chuẩn]" hoặc "[OK] [Đạt]"
            - Nếu có lỗi cần chỉnh sửa, ghi rõ: "[LỖI/CAUTION]" hoặc "[LỖI] [Cần sửa]" hoặc "[LỖI] [Không đạt]"

            Hãy kiểm soát chặt chẽ các mặt sau:
            1. Thể thức văn bản (Nghị định 30/2020): Quốc hiệu, tiêu ngữ, căn lề, font chữ, họ tên chức vụ người ký, nơi nhận, số ký hiệu.
            2. Quy trình rà soát tính hợp pháp và kỹ thuật hệ thống (Nghị định 20/2020): tính chính xác của căn cứ pháp lý, sự tương thích văn bản pháp luật cấp trên, sự rõ ràng trong hiệu lực thi hành văn bản hành chính nội bộ.
            
            Kết xuất ra một danh sách ngắn gọn, có cấu trúc tốt, chỉ rõ từng mục đạt hay lỗi cụ thể để lãnh đạo tra cứu chấm duyệt. Chấm tổng điểm trên thang 10.
        """.trimIndent()

        val prompt = """
            Hãy tiến hành thẩm định và rà soát thể thức kỹ lưỡng văn bản hành chính dưới đây theo Nghị định 30 và Nghị định 20:
            
            -----------------------------------------
            $documentText
            -----------------------------------------
            
            Đưa ra báo cáo thẩm định phân biệt rõ các kết quả đạt chuẩn và các điểm cảnh báo đỏ cần sửa ngay lập tức.
        """.trimIndent()

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    /**
     * Module 4: AI sửa lỗi chính tả và viết lại văn bản hành chính hay hơn
     */
    suspend fun fixSpellingAndRefine(documentText: String, rewriteStyle: String): String {
        val systemPrompt = """
            Bạn là chuyên gia soạn thảo và hiệu đính văn bản cấp cao của lực lượng Công an nhân dân.
            Hãy giúp người dùng:
            1. Phát hiện và sửa sạch lỗi chính tả tiếng Việt.
            2. Viết lại các câu từ sao cho trang trọng, bóng bẩy hành chính, lưu loát, khoa học, chặt chẽ hơn.
            3. Rất quan trọng: TUYỆT ĐỐI không được thay đổi bản chất, ý nghĩa cốt lõi và nội dung sự kiện của văn bản gốc.
        """.trimIndent()

        val prompt = """
            Phong cách viết lại yêu cầu: $rewriteStyle
            Hãy hiệu đính, sửa chính tả và viết lại văn bản sau đây:
            
            -----------------------------------------
            $documentText
            -----------------------------------------
            
            Trả về văn bản đã được tối ưu hoàn chỉnh cùng bảng tóm tắt những điểm cốt lõi đã nâng cấp/sửa lỗi chính tả.
        """.trimIndent()

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    /**
     * Module 5: AI đọc và tóm tắt văn bản dài
     */
    suspend fun summarizeDocument(documentText: String, mode: String): String {
        val systemPrompt = """
            Bạn là cố vấn chiến lược và phân tích văn bản của Ban chỉ huy Phòng Hậu cần Công an tỉnh Đắk Lắk.
            Hãy đọc văn bản dài do cán bộ cung cấp và tóm tắt một cách tối ưu.
        """.trimIndent()

        val prompt = """
            Chế độ tóm tắt yêu cầu: $mode (Ví dụ: "Tóm tắt ngắn gọn", "Chi tiết chuyên sâu", "Rút ý nhanh để báo cáo lãnh đạo").
            
            Nội dung văn bản cần tóm tắt:
            -----------------------------------------
            $documentText
            -----------------------------------------
            
            Hãy đưa ra bản tóm tắt xuất sắc nhất theo đúng chế độ đã chọn, phân tích rõ các điểm mấu chốt, nghĩa vụ hoặc mốc thời gian cần lưu ý.
        """.trimIndent()

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    /**
     * Module 6: OCR từ hình ảnh (Trích xuất văn bản từ ảnh chụp/tài liệu)
     */
    suspend fun performOcr(bitmap: Bitmap): String {
        val systemPrompt = """
            Bạn là một máy quét tài liệu OCR trí tuệ nhân tạo chuyên nghiệp của lực lượng Công an.
            Nhiệm vụ của bạn là đọc ảnh tài liệu, văn bản hành chính được cung cấp và chuyển đổi chính xác từng từ sang định dạng văn bản thô đầy đủ, kể cả bảng biểu nếu có.
            Hãy giữ nguyên cấu trúc dòng và định dạng phân cấp của tài liệu. Không tóm tắt hay bình luận, chỉ trích xuất chữ.
        """.trimIndent()

        val prompt = "Hãy đọc và trích xuất chữ (OCR) chính xác tất cả văn bản xuất hiện trong hình ảnh đính kèm này."

        return callGemini(prompt = prompt, systemInstruction = systemPrompt, bitmap = bitmap)
    }

    /**
     * Module 7: AI Tra cứu các Thông tư, Nghị định, Luật liên quan đến hành chính, hậu cần
     */
    suspend fun searchRegulations(query: String): String {
        val systemPrompt = """
            Bạn là thư viện tra cứu luật và văn bản pháp lý cao cấp của Đội Hậu cần - Quản trị, Phòng Hậu cần Công an tỉnh Đắk Lắk.
            Nhiệm vụ của bạn là tra cứu và giải thích các Luật, Nghị định, Thông tư liên quan đến quản lý tài chính, quản lý tài sản công, hậu cần, kỹ thuật, phương tiện xe cộ, hành chính công vụ của cơ quan Nhà nước và lực lượng Công an nhân dân.
            Hãy đưa ra thông tin tìm kiếm chính xác, trích dẫn rõ số văn bản phát sinh (ví dụ: Nghị định 151/2017/NĐ-CP về tài sản công, Thông tư liên quan, v.v.), điều khoản cụ thể và phân tích cách áp dụng thực tế cho Đội Hậu cần.
        """.trimIndent()

        val prompt = "Hãy giúp tôi tìm kiếm, tra cứu và giải thích quy định có nội dung: $query"

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    /**
     * Module 8: AI Dịch thuật đa ngôn ngữ (chuyên ngành hành chính công và tư pháp)
     */
    suspend fun translateDocument(text: String, targetLanguage: String): String {
        val systemPrompt = """
            Bạn là chuyên gia dịch thuật cao cấp của lực lượng Công an, chuyên dịch các văn bản hành chính, pháp quy, hồ sơ, tài liệu nghiệp vụ sang ngôn ngữ đích.
            Hãy dịch văn bản sát nghĩa, chuyên nghiệp, bảo toàn đầy đủ các từ ngữ pháp lý, thuật từ chuyên ngành và văn phong trang trọng của văn bản hành chính.
        """.trimIndent()

        val prompt = "Hãy dịch văn bản sau đây sang ngôn ngữ: $targetLanguage\n\nNội dung văn bản:\n$text"

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    /**
     * Module 9: AI Chuyển đổi văn bản cũ sang mẫu mới (chuẩn hóa theo Nghị định 30/2020)
     */
    suspend fun convertToNewTemplate(oldDocumentText: String, newTemplateType: String): String {
        val systemPrompt = """
            Bạn là Ban chỉ huy tối cao về chuẩn hóa thể thức của Đội Hậu cần - Quản trị, Phòng Hậu cần Công an tỉnh Đắk Lắk.
            Hãy chuyển đổi văn bản kiểu cũ hoặc thô sơ do cán bộ cung cấp sang mẫu văn bản hành chính mới ($newTemplateType) chuẩn mực, đúng 100% quy định Nghị định 30/2020/NĐ-CP của Chính phủ.
            Cơ cấu lại bố cục: quốc hiệu, tiêu ngữ, cơ quan ban hành (CÔNG AN TỈNH ĐẮK LẮK / PHÒNG HẬU CẦN / ĐỘI HC-QT), địa danh Đắk Lắk, ngày tháng hiện tại, số hiệu, bố cục các chương mục, căn cứ pháp lý, nội dung phân tích rõ nghĩa, nơi nhận và chức vụ lãnh đạo ký đúng mực.
        """.trimIndent()

        val prompt = "Hãy lấy văn bản cũ sau đây và chuyển đổi hoàn hảo nó sang dạng văn bản hành chính mẫu mới [$newTemplateType] chuẩn Nghị định 30:\n\n$oldDocumentText"

        return callGemini(prompt = prompt, systemInstruction = systemPrompt)
    }

    // Helper extension to convert Bitmap to Base64 String
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
