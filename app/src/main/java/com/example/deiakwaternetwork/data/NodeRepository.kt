import android.util.Log
import com.example.deiakwaternetwork.data.RetrofitClient
import com.example.deiakwaternetwork.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context


class NodeRepository(private val context: Context) {
    private val apiService = RetrofitClient.getApiService(context)


    suspend fun getNodes(): List<Node>? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getNodes()
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("NodeRepository", "Error fetching nodes: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("NodeRepository", "Error fetching nodes: ${e.message}")
                null
            }
        }
    }

    suspend fun createNode(node: Node): Node? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.createNode(node)
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("NodeRepository", "Error creating node: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("NodeRepository", "Exception creating node: ${e.message}")
                null
            }
        }
    }


    suspend fun updateNode(id: String, node: Node): Node? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.updateNode(id, node)
                if (response.isSuccessful) {
                    response.body()
                } else {
                    Log.e("NodeRepository", "Error updating node: ${response.code()} ${response.message()}")
                    null
                }
            } catch (e: Exception) {
                Log.e("NodeRepository", "Error updating node: ${e.message}")
                null
            }
        }
    }

    suspend fun deleteNode(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.deleteNode(id)
                response.isSuccessful
            } catch (e: Exception) {
                Log.e("NodeRepository", "Error deleting node: ${e.message}")
                false
            }
        }
    }
}