/*
 * Copyright 2018 Emmanouil Gkatziouras
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zextras.maven.s3storage.plugin.upload;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.zextras.maven.s3storage.EndpointProperty;
import com.zextras.maven.s3storage.PathStyleEnabledProperty;
import com.zextras.maven.s3storage.utils.S3Connect;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.wagon.authentication.AuthenticationException;

@Mojo(name = "s3-upload")
public class S3UploadMojo extends AbstractMojo {

    @Parameter( property = "s3-upload.bucket")
    private String bucket;

    @Parameter(property = "s3-upload.path")
    private String path;

    @Parameter(property = "s3-upload.key")
    private String key;

    @Parameter(property = "s3-upload.region")
    private String region;

    public S3UploadMojo() {
    }

    /**
     * If the path is a file then a file shall be uploaded. If the file specified is a directory then the directory shall
     * be uploaded using prefix and the rest files shall be uploaded recursively
     *
     * @param bucket the name of the bucket
     * @param path   the path where to save the file
     * @param key    AWS key
     * @param region AWS region
     */
    public S3UploadMojo(String bucket, String path, String key, String region) {
        this.bucket = bucket;
        this.path = path;
        this.key = key;
        this.region = region;
    }

    /**
     * At least the bucket should be null or else everything else shall be fetched
     * @throws MojoExecutionException
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (bucket == null) {
            throw new MojoExecutionException("You need to specify a bucket for the s3-upload goal configuration");
        }

        AmazonS3 amazonS3;
        try {
            //Sending the authenticationInfo as null will make this use the default S3 authentication, which will only
            //look at the environment Java properties or environment variables
            amazonS3 = S3Connect.connect(null, region, EndpointProperty.empty(),
              new PathStyleEnabledProperty(String.valueOf(S3ClientOptions.DEFAULT_PATH_STYLE_ACCESS)));
        } catch (AuthenticationException e) {
            throw new MojoExecutionException(
                    String.format("Unable to authenticate to S3 with the available credentials. Make sure to either define the environment variables or System properties defined in https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html.%n" +
                            "Detail: %s", e.getMessage()),
                    e);
        }

        if(isDirectory()){
            List<String> filesToUpload = findFilesToUpload(path);

            for(String fileToUpload: filesToUpload) {
                keyUpload(amazonS3, generateKeyName(fileToUpload), new File(fileToUpload));
            }
        } else {
            keyUpload(amazonS3, keyIfNull(), new File(path));
        }
    }
    /**
     * Uploads the given file to the path given by {@code keyName} on the configured {@link #bucket}. Note that the
     * ObjectMetadata objectMetadata = new ObjectMetadata(); content-length and content-type of the uploaded file will
     * be inferred by the AWS SDK's implementation.
     *
     * @param amazonS3 the API object to use for the upload
     * @param keyName  the path in the bucket where the given file will be stored at
     * @param file     the file to upload
     * @see AmazonS3#putObject(String, String, File)
     */
    private void keyUpload(AmazonS3 amazonS3, String keyName, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, keyName, file);
        amazonS3.putObject(putObjectRequest);
    }

    private List<String> findFilesToUpload(String filePath) {
        List<String> totalFiles = new ArrayList<>();

        File file = new File(filePath);

        if(file.isDirectory()) {
            File[] files = file.listFiles();

            if (files != null) {
                for (File lFile : files) {
                    if (lFile.isDirectory()) {
                        List<String> filesFound = findFilesToUpload(lFile.getAbsolutePath());
                        totalFiles.addAll(filesFound);
                    } else {
                        totalFiles.add(lFile.getAbsolutePath());
                    }
                }
            }

        } else {
            totalFiles.add(file.getAbsolutePath());
        }

        return totalFiles;
    }

    private boolean isDirectory() {
        return new File(path).isDirectory();
    }

    private String generateKeyName(String fullFilePath) {
        StringBuilder keyNameBuilder = new StringBuilder();

        String absolutePath = new File(path).getAbsolutePath();

        if(key!=null) {
            keyNameBuilder.append(key);
            if(!fullFilePath.startsWith("/")) {
                keyNameBuilder.append("/");
            }
            keyNameBuilder.append(fullFilePath.replace(absolutePath,""));
        } else {
            final String clearFilePath = fullFilePath.replace(absolutePath,"");
            final String filePathToAppend = clearFilePath.startsWith("/")? clearFilePath.replaceFirst("/",""):clearFilePath;
            keyNameBuilder.append(filePathToAppend);
        }
        return keyNameBuilder.toString();
    }

    private String keyIfNull() {
        if(key==null) {
            return new File(path).getName();
        } else {
            return key;
        }
    }

}
