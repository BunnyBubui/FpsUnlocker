package com.fpsunlocker.tool;

// อินเทอร์เฟซของ FileService ที่ Shizuku รันในโปรเซสแยกด้วยสิทธิ์ shell
// ทุกเมธอดต้องกำหนดหมายเลข transaction เอง เพราะ destroy() ต้องเป็น 16777114 ตามที่ Shizuku กำหนด
interface IFileService {
    void destroy() = 16777114;
    void exit() = 1;

    String readText(String path) = 2;
    void writeText(String path, String content) = 3;
    void copy(String src, String dst) = 4;
    boolean exists(String path) = 5;
    long size(String path) = 6;
    List<String> listFiles(String dir, int maxDepth, int limit) = 7;
    String exec(String command) = 8;
}
