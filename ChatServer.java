package com.rain.y;

import org.w3c.dom.ls.LSInput;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChatServer {
    // 存储每个客户端的 PrintWriter 和 昵称
    private static final List<PrintWriter> clientWrites = new ArrayList<>();
    //用于同步
    private static final Object lock = new Object();
    //储存每个客户端的PrintWriter和Name
    private static final Map<PrintWriter, String> clientName = new HashMap<>();

    //广播方向：向所有客户端发送消息
    private static void broadcast(String message, PrintWriter sender) {
        synchronized (lock) {
            for (PrintWriter writer : clientWrites) {
                System.out.println("当前 clientWrites 大小: " + clientWrites.size());
                if (writer != sender) { //不发给发送者自己（可选）
                    writer.println(message);
                    writer.flush();           //手动刷新缓冲区！
                }
            }
        }
    }

    public static void main(String[] args) {
        ServerSocket serverSocket = null;

        try {
            //1.创建ServerSocket，监听8888窗口
            serverSocket = new ServerSocket(8888);
            System.out.println("[服务器]启动成功，监听端口 8888...");
            System.out.println("等待客户段接入");

            //2.开始接受客户端连接（阻塞等待）
            //持续监听新客户端
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("客户端接入:" + clientSocket.getRemoteSocketAddress());

                //为每个客户端开一个新线程处理
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();

            }


        } catch (IOException e) {
            System.out.println("服务器异常" + e.getMessage());

        } finally {
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 内部类：处理单个客户端通信
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        @Override
        public void run() {
            //初始io流
            try {
                ///====第一步：接收客户端昵称=====
                out.println("请输入您的名称：");
                String nickname = in.readLine();
                if (nickname == null || nickname.trim().isEmpty()) {
                    nickname = "匿名用户";
                }
                nickname = nickname.trim();

                //加锁，防止并发
                synchronized (lock) {
                    clientWrites.add(out);
                    clientName.put(out, nickname);//记录名称
                }

                //通知所有人：有人上线了
                broadcast("[系统]欢迎" + "[" + nickname + "]" + "加入聊天室", out);

                /// =====第二步：正常消息循环=====
                String msg;
                while ((msg = in.readLine()) != null) {
                    if ("bye".equalsIgnoreCase(msg.trim())) {
                        break;
                    }
                    //新增：处理 /users 命令
                    if ("/users".equals(msg.trim())) {
                        StringBuilder userList = new StringBuilder("[在线用户] ");
                        synchronized (lock) {
                            List<String> names = new ArrayList<>(clientName.values());
                            userList.append(String.join(",", names));
                        }
                        out.println(userList.toString());
                        continue;
                    }
                    //查看谁在线
                    if (msg.startsWith("/whois")) {
                        String queryName = msg.substring(7).trim();
                        boolean online = clientName.containsValue(queryName);
                        out.println("[系统] 当前" + queryName + (online ? "在线" : "不在线"));
                    }
                    //帮助命令/help
                    if ("/help".equals(msg)) {
                        out.println("[可用命令]");
                        out.println(" /users   -查看在线用户");
                        out.println(" /quit    -退出聊天");
                        out.println(" /whois   -查看<昵称>用户是否在线");
                        out.println("@用户名 消息   -私聊");
                        continue;
                    }

                    //防止昵称相同
                    synchronized (lock) {
                        String originalName = nickname;
                        while (clientName.containsValue(nickname)) {
                            out.println("昵称[" + nickname + "]" + "已重复，请换一个");
                            String newName = in.readLine();
                            if (newName != null || newName.trim().isEmpty()) {
                                nickname = originalName + System.currentTimeMillis() % 1000; // 防无限循环
                                break;
                            }
                            nickname = newName.trim();
                            clientName.put(out, nickname); //更新昵称
                        }

                    }

                    // 私聊、广播等其他逻辑...
                    if (msg.startsWith("@")) {
                        int spaceIndex = msg.indexOf(" ");
                        if (spaceIndex == -1) {
                            out.println("[系统]私聊格式错误：@昵称 内容");
                        } else {
                            // 提取名称
                            String targetName = msg.substring(1, spaceIndex).trim();
                            //提取内容
                            String privateMessage = msg.substring(spaceIndex + 1).trim();

                            if (privateMessage.isEmpty()) {
                                out.println("[系统] 私聊消息不能为空");
                                continue;
                            }
                            //获取发送者名称
                            String senderName = clientName.get(out);
                            //接收者的流
                            PrintWriter targetWriter = null;

                            for (Map.Entry<PrintWriter, String> entry : clientName.entrySet()) {
                                if (entry.getValue().equals(targetName)) {
                                    targetWriter = entry.getKey();
                                    break;
                                }
                            }
                            //发送私聊消息
                            if (targetWriter != null) {
                                //发给接收者
                                targetWriter.println("[私聊][" + senderName + "-->" + targetName + "]" + privateMessage);
                                targetWriter.flush();
                                //回显给自己
                                out.println("[私聊] 你-->[" + targetName + "]" + privateMessage);
                            } else {
                                out.println("[系统] 用户 [" + targetName + "] 不在线或不存在");
                            }
                        }
                        //普通消息广播给所有人
                    } else {
                        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                        broadcast("[" + time + "]" + "[" + nickname + "]:" + msg, out);
                    }

                }
            } catch (IOException e) {
                System.err.println("与客户端通信出错" + e.getMessage());
                e.printStackTrace();
            } finally {
                ///===第三步：清理资源=====
                //客户端断开，从列表移除
                synchronized (lock) {
                    clientWrites.remove(out);
                    String nickname = clientName.remove(out);//移除昵称
                    if (nickname != null) {
                        broadcast("系统：[" + nickname + "] 离开了聊天室", out);
                    }
                }
                closeResources();


            }
        }

        private void closeResources() {
            try {
                if (socket != null) socket.close();
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (IOException e) {
                System.err.println("出错了：" + e.getMessage());
            }
        }

    }
}
