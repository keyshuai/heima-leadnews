package com.heima.minio;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class MinIOTest {
    public static void main(String[] args) {
        FileInputStream fileInputStream =null;

        try {
            fileInputStream=new FileInputStream("D:\\list.html");
            //1.创建minio链接客户端
            MinioClient minioClient = MinioClient.builder().credentials("minio", "minio123")
                    .endpoint("http://192.168.200.188:9000").build();
            //2.上传
            PutObjectArgs putObjectArgs = PutObjectArgs.builder().
                    object("list.html")
                    .contentType("text/html")
                    .bucket("leadnew")
                    .stream(fileInputStream, fileInputStream.available(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
