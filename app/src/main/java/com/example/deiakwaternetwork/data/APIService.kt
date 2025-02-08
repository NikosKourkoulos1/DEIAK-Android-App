
package com.example.deiakwaternetwork.data

import com.example.deiakwaternetwork.model.* // Import your data classes
import com.example.deiakwaternetwork.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.*

interface APIService {

    // --- Authentication ---

    @POST("api/auth/register")
    suspend fun register(@Body registerRequest: RegisterRequest): Response<Unit>

    @POST("api/auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>


    // --- Nodes ---

    @GET("api/network/nodes/search")
    suspend fun getNodes(
        @Query("type") type: String? = null,
        @Query("minLatitude") minLatitude: Double? = null,
        @Query("maxLatitude") maxLatitude: Double? = null,
        @Query("minLongitude") minLongitude: Double? = null,
        @Query("maxLongitude") maxLongitude: Double? = null,
        @Query("minCapacity") minCapacity: Int? = null,
        @Query("maxCapacity") maxCapacity: Int? = null,
        @Query("status") status: String? = null,
        @Query("name") name: String? = null
    ): Response<List<Node>>

    @POST("api/network/node")
    suspend fun createNode(@Body node: Node): Response<Node>

    @PUT("api/network/node/{id}")
    suspend fun updateNode(@Path("id") nodeId: String, @Body updatedNode: Node): Response<Node>

    @DELETE("api/network/node/{id}")
    suspend fun deleteNode(@Path("id") id: String): Response<Unit>

    @GET("api/user/{id}")
    suspend fun getUser(@Path("id") userId: String): Response<User>

    @PUT("api/user/{id}")
    suspend fun updateUser(
        @Path("id") userId: String,
        @Body user: User
    ): Response<User>

    @DELETE("api/user/{id}")
    suspend fun deleteUser(@Path("id") userId: String): Response<Unit>
    @GET("api/user/users")
    suspend fun getAllUsers(): Response<List<User>>

    //Pipes
    @GET("api/network/pipes") //Corrected the URLs.
    suspend fun getPipes(): Response<List<Pipe>>

    @POST("api/network/pipe")
    suspend fun createPipe(@Body pipe: Pipe): Response<Pipe>

    @PUT("api/network/pipe/{id}")
    suspend fun updatePipe(@Path("id") id: String, @Body pipe: Pipe) : Response<Pipe>

    @DELETE("api/network/pipe/{id}")
    suspend fun deletePipe(@Path("id") id: String): Response<Void>

    @GET("api/network/pipes/search")
    suspend fun searchPipes(
        @Query("status") status: String?,
        @Query("minFlow") minFlow: Double?,
        @Query("maxFlow") maxFlow: Double?,
        @Query("minLength") minLength: Double?,
        @Query("maxLength") maxLength: Double?
    ): Response<List<Pipe>>



}