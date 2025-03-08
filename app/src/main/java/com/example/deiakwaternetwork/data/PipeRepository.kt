import android.content.Context
import android.util.Log
import com.example.deiakwaternetwork.data.RetrofitClient
import com.example.deiakwaternetwork.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipeRepository(private val context: Context) {
    private val apiService = RetrofitClient.getApiService(context)

    suspend fun getPipes(): List<Pipe>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPipes()
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("PipeRepository", "Error fetching pipes: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("PipeRepository", "Error fetching pipes: ${e.message}")
                null
            }
        }
    }

    suspend fun createPipe(pipe: Pipe): Pipe? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.createPipe(pipe)
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("PipeRepository", "Error creating pipe: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("PipeRepository", "Error creating pipe: ${e.message}")
                null
            }
        }
    }

    suspend fun updatePipe(id: String, pipe: Pipe): Pipe? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updatePipe(id, pipe)
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("PipeRepository", "Error updating pipe: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("PipeRepository", "Error updating pipe: ${e.message}")
                null
            }
        }
    }

    suspend fun deletePipe(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deletePipe(id)
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("PipeRepository", "Error deleting pipe: ${e.message}")
                false
            }
        }
    }
}