//package com.openai.commonutil;
//
//import java.io.ByteArrayOutputStream;
//import java.io.DataOutputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//
//public class PCMToWAV {
//    public static void convertToWAV(String pcmFilePath,  int sampleRate, int channels, int bitsPerSample) {
//        ByteArrayOutputStream
//        try (ByteArrayOutputStream dos = new DataOutputStream(new FileOutputStream(wavFilePath));
//             FileOutputStream fis = new FileOutputStream(pcmFilePath)) {
//
//            // RIFF header
//            dos.writeBytes("RIFF");
//            dos.writeInt(0); // File size placeholder
//            dos.writeBytes("WAVE");
//
//            // fmt sub-chunk
//            dos.writeBytes("fmt ");
//            dos.writeInt(16); // Sub-chunk size
//            dos.writeShort(1); // Audio format (PCM)
//            dos.writeShort((short) channels);
//            dos.writeInt(sampleRate);
//            dos.writeInt(sampleRate * channels * bitsPerSample / 8);
//            dos.writeShort((short) (channels * bitsPerSample / 8));
//            dos.writeShort((short) bitsPerSample);
//
//            // data sub-chunk
//            dos.writeBytes("data");
//            dos.writeInt(0); // Data size placeholder
//
//            // Write PCM data
//            byte[] buffer = new byte[1024];
//            int bytesRead;
//            while ((bytesRead = fis.read(buffer)) != -1) {
//                dos.write(buffer, 0, bytesRead);
//            }
//
//            // Update file size
//            dos.close();
//            fis.close();
//
//            // Update file size placeholder
//            File wavFile = new File(wavFilePath);
//            int fileSize = (int) wavFile.length();
//            updateFileSize(wavFilePath, fileSize);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static void updateFileSize(String filePath, int fileSize) throws IOException {
//        try (FileOutputStream fos = new FileOutputStream(filePath, true)) {
//            fos.getChannel().position(4);
//            fos.writeInt(fileSize - 8);
//        }
//    }
//}