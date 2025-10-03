package com.rain.y;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient {
    public static void main(String[] args) {
        try (
                //主动连接AcceptServer
                Socket socket = new Socket("127.0.0.1", 8888);

                //可以发送消息也能接收
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                //接收服务器传来的
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                //键盘输出：读取键盘的
                BufferedReader console = new BufferedReader(
                        new InputStreamReader(System.in)
                )
        ) {
            /// === 先读取服务器的昵称提示 ===
            System.out.print(in.readLine()); // "请输入您的昵称："
            String nickname = console.readLine();
            //发送给服务器
            out.println(nickname);

            /// === 启动接收线程（异步接收消息）===
            Thread receiveThread = new Thread(() -> {
                try {
                    String receive;
                    while ((receive = in.readLine()) != null) {
                        System.out.println("\n" + receive);//显示消息
                        if ("bye".equals(receive)) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("连接已断开：");
                }
            });
            receiveThread.start();

            /// === 主线程发送消息 ===
            String line;
            while ((line = console.readLine()) != null) {
                out.println(line);
                if ("bye".equalsIgnoreCase(line) || "/quit".equals(line)) {
                    break;
                }

            }

        } catch (Exception e) {
            System.err.println("客户端错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
