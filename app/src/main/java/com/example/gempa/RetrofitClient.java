package com.example.gempa;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL = "https://data.bmkg.go.id/";
    private static Retrofit retrofit = null;

    // OkHttp client dengan no-cache agar data selalu fresh dari BMKG
    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder()
                                .header("Cache-Control", "no-cache, no-store")
                                .header("Pragma", "no-cache")
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(buildClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static EarthquakeApiService getApiService() {
        return getInstance().create(EarthquakeApiService.class);
    }
}