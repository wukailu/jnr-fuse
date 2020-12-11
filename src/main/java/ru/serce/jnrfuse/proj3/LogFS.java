package ru.serce.jnrfuse.proj3;


import com.sun.javafx.geom.transform.Identity;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.*;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static jnr.ffi.Platform.OS.WINDOWS;

public class LogFS extends FuseStubFS {
    private ByteBuffer mem;
    private int mark;
    private int inodeCnt;
    /* The total size of FS, counted in Byte */
    private final int total_size;

    private Checkpoint checkpoint1, checkpoint2, checkpoint;
    private Map<Integer, Integer> oldInodeMap, newInodeMap;

    LogFS(int total_size){
        this(ByteBuffer.allocate(total_size));
    }

    // Load from existing mem
    LogFS(ByteBuffer mem){
        this.mem = mem;
        mark(1024);
        this.total_size = mem.capacity();
        checkpoint1 = new Checkpoint();
        checkpoint2 = new Checkpoint();
        checkpoint1.parse(mem, this.total_size-2048, 1024);
        checkpoint2.parse(mem, this.total_size-1024, 1024);
        if (checkpoint1.valid())
            checkpoint = checkpoint1;
        else
            checkpoint = checkpoint2;
        oldInodeMap = new HashMap<Integer, Integer>();
        newInodeMap = new HashMap<Integer, Integer>();
        inodeCnt = 0;
        int lastInodeMap = checkpoint.lastInodeMap;
        while (lastInodeMap > 0){
            InodeMap inodeMap = new InodeMap().parse(mem, lastInodeMap, 1024);
            oldInodeMap.putAll(inodeMap.inodeMap);
            lastInodeMap = inodeMap.preInodeMapAddress;
        }
        for(int i: oldInodeMap.values())
            inodeCnt = Math.max(inodeCnt, i);
        if (inodeCnt == 0){ // create "/"
            createDirectory(0777, Inode.Identity);
        }
    }

    private void reset(){
        mem.position(mark).mark();
    }

    private void mark(int newMark){
        mark = newMark;
        mem.position(newMark).mark();
    }

    /***
     * @param inode The inode number
     * @return  Return the Address of the start of the inode, e.g. 0x18237c00.
     */
    private int getInodeAddress(int inode) throws Exception {
        Object ret = newInodeMap.get(inode);
        if (ret == null)
            ret = oldInodeMap.get(inode);
        if (ret == null)
            throw new Exception("Inode not found in Inode Map!");
        return (int) ret;
    }

    /***
     * Create an inode and write data in dataBuffer in this inode.
     * @param dataBuffer    The data that need to write to disk, the new inode is point to this data.
     * @param handler       The handler for inode, e.g. modify mode or some value else.
     * @return              The inode of the new inode
     */
    private Inode createInode(ByteBuffer dataBuffer, Inode.Handler handler){
        int inodeNumber = ++inodeCnt;
        Inode newInode = new Inode(inodeNumber);
        newInode = handler.process(newInode);
        write(newInode, dataBuffer, 0, dataBuffer.remaining());
        update(newInode);
        return newInode;
    }

    /***
     * Create a directory with mode.
     * @param mode  mode of directory. Check FileStat to understand meaning.
     * @return  Return the inode of the directory.
     */
    private Inode createDirectory(long mode, Inode.Handler handler){
        ByteBuffer dataBuffer = ByteBuffer.allocate(1024);
        DirectoryBlock directoryBlock = new DirectoryBlock();
        directoryBlock.flush(dataBuffer);
        dataBuffer.flip();
        Inode.Handler newHandler = Inode.Sequential(
            handler,
            Inode.SetMode((int) mode | FileStat.S_IFDIR)
        );
        return createInode(dataBuffer, newHandler);
    }

    /***
     * Update an inode. Write it to disk and update the inodeMap.
     * @param node      The new Inode.
     * @return          The new address on disk where it write to.
     */
    private synchronized int update(Inode node){
        int ret = mark;
        reset();
        node.flush(mem);
        mark(mem.position());
        newInodeMap.put(node.id, ret);
        return ret;
    }

//  TODO: Add Notes.
    private boolean checkPrivilege(Inode inode, int mask, FuseContext context) {
        int flag = (int) (inode.mode & 0x7);
        if (context != null) {
            if (context.uid.intValue() == inode.uid)
                flag |= (inode.mode >> 6) & 0x7;
            if (context.gid.intValue() == inode.gid)
                flag |= (inode.mode >> 3) & 0x7;
        }
        return (flag & mask) == mask;
    }

    /***
     * The inode number of a file or directory.
     * @param path  The path, in Linux form.
     * @param mask  The permission needed for this access
     * @return  The inode number of this. -1 for invalid path.
     */
    private Inode inodeOf(String path, int mask) throws Exception {
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.equals(""))
            return inodeOf(1);
        String[] pathList = path.split("/");
        int currentInode = 1;
        for (String s: pathList) {
            Inode inode = inodeOf(currentInode);
            if ((inode.mode & FileStat.S_IFDIR) == 0) {
                throw new Exception(String.valueOf(-ErrorCodes.ENOENT()));
            }
            if (!checkPrivilege(inode, mask, getContext())) {
                throw new Exception(String.valueOf(-ErrorCodes.EACCES()));
            }
            DirectoryBlock directory = new DirectoryBlock().parse(inode.read(mem, 0, 1024), 0, 1024);
            Object ret = directory.contents.get(s);
            if(ret == null)
                throw new Exception(String.valueOf(-ErrorCodes.ENOENT()));
            currentInode = (int) ret;
        }
        return inodeOf(currentInode);
    }

    /***
     * The Inode Object of a inode id.
     * @param id Inode ID.
     * @return  The Inode.
     */
    private Inode inodeOf(int id) throws Exception {
        return new Inode(id).parse(mem, getInodeAddress(id), 1024);
    }

    /***
     * Read the dataBlock of the inode, and parse into a Directory Block.
     * @param node   The inode of Directory.
     * @return  A DirectoryBlock.
     */
    private DirectoryBlock getDirectory(Inode node) throws Exception {
        if ((node.mode & FileStat.S_IFDIR) != 0)
            return new DirectoryBlock().parse(read(node, 0, 1024));
        else
            throw new Exception(String.valueOf(-ErrorCodes.ENOENT()));
    }

    /***
     * Read dataBlock in the inode.
     * @param node          The inode.
     * @param readOffset    The offset for the file. 0 means read from the start of the file.
     * @param size          The size needed to read.
     * @return              A ByteBuffer contain the data.
     */
    private ByteBuffer read(Inode node, int readOffset, int size) {
        return node.read(mem, readOffset, size);
    }

    /***
     * Write data to an inode, the data and the modified inode will both write to the disk.
     * @param node          The inode.
     * @param buffer        The buffer containing data needed to write. Write from the buffer.position().
     * @param writeOffset   The offset of the write. 0 means write from the beginning of the file.
     * @param size          The size of data need to write to disk in buffer.
     */
    private synchronized void write(Inode node, ByteBuffer buffer, int writeOffset, int size) {
        reset();
        node.write(buffer, mem, writeOffset, size);
        mark(mem.position());
        update(node);
    }

    //------

    private class MemoryDirectory extends MemoryFile {
        private DirectoryBlock data;
        /***
         * In order to make this thread safe, don't storage the MemoryPath!!! Allocate it every time!!!
         * @param path Path of this.
         * @param mask Permission needed to get this.
         * @throws Exception If there's error will return the error value as a String.
         */
        protected MemoryDirectory(String path, int mask) throws Exception {
            super(path, mask);
            data = getDirectory(inode);
        }

        protected MemoryDirectory(MemoryFile file) throws Exception {
            super(file);
            data = getDirectory(inode);
        }

        protected void read(Pointer buf, FuseFillDir filler) throws Exception {
            for (String p : data.contents.keySet()) {
                filler.apply(buf, p, null, 0);
            }
        }

        protected void rename(String oldChildName, String newChildName){
            Integer o = data.contents.remove(oldChildName);
            data.contents.put(newChildName, o);
        }

        protected void delete(String childName){
            data.contents.remove(childName);
        }

        protected void add(String childName, int childID){
            data.contents.put(childName, childID);
        }

        protected void createFile(String fileName, ByteBuffer data, Inode.Handler handler) throws Exception {
            Inode inode = createInode(data, handler);
            add(fileName, inode.id);
        }

        protected void createDirectory(String directoryName, long mode, Inode.Handler handler) throws Exception{
            Inode inode = LogFS.this.createDirectory(mode, handler);
            add(directoryName, inode.id);
        }

        @Override
        protected void flush(){
            if (data != null){
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                data.flush(buffer);
                buffer.flip();
                LogFS.this.write(inode, buffer, 0, 1024);
            }
            super.flush();
        }
    }

    private class MemoryFile {
        protected int id;
        protected Inode inode;

        /***
         * In order to make this thread safe, don't storage the MemoryPath!!! Allocate it every time!!!
         * @param path Path of this.
         * @param mask Permission needed to get this.
         * @throws Exception If there's error will return the error value as a String.
         */
        protected MemoryFile(String path, int mask) throws Exception {
            this.inode = inodeOf(path, mask);
            this.id = this.inode.id;
        }

        protected MemoryFile(int inodeID) throws Exception {
            this.inode = inodeOf(inodeID);
            this.id = this.inode.id;
        }

        protected MemoryFile(MemoryFile file){
            this.id = file.id;
            this.inode = file.inode;
        }

        protected boolean isDiretory() {
            return (inode.mode & FileStat.S_IFDIR) != 0;
        }

        // TODO: fix the permission later
        protected void getattrCommon(FileStat stat) {
            // TODO: Fix bug here, which will lead to unable to see context in file.
            stat.st_mode.set(inode.mode);
            stat.st_uid.set(inode.uid);
            stat.st_gid.set(inode.gid);

            stat.st_ctim.tv_nsec.set(inode.lastChangeTime);
            stat.st_atim.tv_nsec.set(inode.lastAccessTime);
            stat.st_mtim.tv_nsec.set(inode.lastModifyTime);
        }

        protected void getattr(FileStat stat) {
            getattrCommon(stat);
            if (!isDiretory())
                stat.st_size.set(inode.size);
        }

        protected void read(Pointer buffer, long size, long offset) {
            int bytesToRead = (int) Math.min(inode.size - offset, size);
            byte[] bytesRead = inode.read(mem, (int) offset, bytesToRead).array();
            synchronized (this) {
                buffer.put(0, bytesRead, 0, bytesToRead);
            }
        }

        protected synchronized void truncate(long size) {
            // TODO: Bug fix here for kailu
            if (size < inode.size) {
                inode.updateSize((int) size);
            }
        }

        protected void write(Pointer buffer, long bufSize, long writeOffset) {
            byte[] bytesToWrite = new byte[(int) bufSize];
            synchronized (this) {
                buffer.get(writeOffset, bytesToWrite, 0, (int) bufSize);
                LogFS.this.write(inode, ByteBuffer.wrap(bytesToWrite), (int) 0, (int) bufSize);
            }
        }

        protected void flush(){
            if (inode != null){
                update(inode);
            }
        }
    }

    private MemoryDirectory getParentDirectory(String path, int mask) throws Exception {
        return new MemoryDirectory(getParentComponent(path), mask);
    }

    private static class Logger {
        public String info;
        private final int type;

        Logger() {
            this(0);
        }

        Logger(int type) {
            this.type = type;
        }

        public void log(String subinfo) {
            if (type == 0) {
                System.out.println(subinfo);
            } else if (type == 1) {
                info += subinfo + '\n';
            }
        }
    }

    private final Logger logger = new Logger();

    public static void main(String[] args) {
        LogFS memfs = new LogFS(1024 * 1024 * 100);
        memfs.selfTest();
        try {
            String path;
            if (Platform.getNativePlatform().getOS() == WINDOWS) {
                if (!System.getProperty("file.encoding").equals("UTF-8"))
                    System.out.println("UTF-8 encoding required! Current encoding: " + System.getProperty("file.encoding"));
                path = "J:\\";
            } else {
                path = "/tmp/mnt";
            }
            memfs.mount(Paths.get(path), true, true);
        } finally {
            memfs.umount();
        }
    }

    private class Tester{
        private void createFile(String path, String text) throws Exception {
            MemoryDirectory directory = getParentDirectory(path, 0);
            directory.createFile(getLastComponent(path), ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)), Inode.SetMode(0777 | FileStat.S_IFREG));
            directory.flush();
        }

        private void createDirectory(String path) throws Exception {
            MemoryDirectory directory = new MemoryDirectory(getParentComponent(path), 0);
            directory.createDirectory(getLastComponent(path), 0777, Inode.Identity);
            directory.flush();
        }
    }

    public void selfTest() {
        Tester tester = new Tester();
        try{
            // Sprinkle some files around
            tester.createFile("/Sample file.txt", "Hello there, feel free to look around.\n");
            tester.createDirectory("/Sample directory");
            tester.createDirectory("/Directory with files");
            tester.createFile("/Directory with files/hello.txt", "This is some sample text.\n");
            tester.createFile("/Directory with files/hello again.txt", "This another file with text in it! Oh my!\n");
            tester.createDirectory("/Directory with files/Sample nested directory");
            tester.createFile("/Directory with files/Sample nested directory/So deep.txt",
                "Man, I'm like, so deep in this here file structure.\n");
        } catch (Exception e){
            logger.log("Self Test Failed!!!");
            logger.log(e.toString());
        }

    }

    private static String getLastComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private static String getParentComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        assert !path.isEmpty();
        return path.substring(0, path.lastIndexOf("/"));
    }

    @Override
    public int getattr(String path, FileStat stat) {
        logger.log("[INFO]: getattr, " + mountPoint + path);
        try{
            MemoryFile p = new MemoryFile(path, 0);
            if (p.isDiretory())
                p = new MemoryDirectory(p);
            p.getattr(stat);
            return 0;
        }catch (Exception e) {
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int chmod(String path, @mode_t long mode) {
        logger.log("[INFO]: chmod, " + path + ", " + mode);
        // TODO: Implement this
        // FIXME: check the privilege
        return 0;
    }

    @Override
    public int chown(String path, @uid_t long uid, @gid_t long gid) {
        logger.log("[INFO]: chown, " + path + ", " + uid + ", " + gid);
        try{
            MemoryFile p = new MemoryFile(path, 0);
            // FIXME: check the privilege
            if ((int) uid != -1) {
                p.inode.uid = (int) uid;
            }
            if ((int) gid != -1) {
                p.inode.gid = (int) gid;
            }
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        logger.log("[INFO]: utimens, " + path + ", " + timespec);
        assert timespec.length == 2 : "the length of argument timespec is not 2.";
        try{
            MemoryFile p = new MemoryFile(path, 0);
            // FIXME: check the privilege
            p.inode.lastAccessTime = timespec[0].tv_nsec.longValue();
            p.inode.lastModifyTime = timespec[1].tv_nsec.longValue();
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int access(String path, int mask) {
        logger.log("[INFO]: access, " + path + ", " + mask);
//        System.out.println(AccessConstants.F_OK + ", " + AccessConstants.R_OK + ", " + AccessConstants.W_OK + ", " + AccessConstants.X_OK);
        try{
            MemoryFile p = new MemoryFile(path, 0);
            Inode inode = p.inode;
            // FIXME: check the privilege
            FuseContext context = getContext();
            int flag = 0;
            if (context.uid.intValue() == inode.uid) {
                flag |= (inode.mode >> 6) & 0x7;
            }
            if (context.gid.intValue() == inode.gid) {
                flag |= (inode.mode >> 3) & 0x7;
            }
            flag |= (inode.mode) & 0x7;
            p.flush();
            if ((flag & mask) != mask)
                return -ErrorCodes.EACCES();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        logger.log("[INFO]: create, " + mountPoint + path);
        try{
            MemoryDirectory p = getParentDirectory(path, 0);
            p.createFile(getLastComponent(path), ByteBuffer.allocate(0), Inode.SetMode((int) mode));
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        logger.log("[INFO]: mkdir, " + mountPoint + path);
        try{
            MemoryDirectory p = getParentDirectory(path, 0);
            p.createDirectory(getLastComponent(path), mode, Inode.Identity);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: read, " + mountPoint + path + ", " + buf + ", " + size + ", " + offset);
        try{
            MemoryFile p = new MemoryFile(path, 0);
            if (p.isDiretory()) {
                return -ErrorCodes.EISDIR();
            }
            p.read(buf, size, offset);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: readdir, " + mountPoint + path + ", " + buf + ", " + offset);
        try{
            MemoryDirectory p = new MemoryDirectory(path, 0);
            filter.apply(buf, ".", null, 0);
            filter.apply(buf, "..", null, 0);
            p.read(buf, filter);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        logger.log("[INFO]: statfs, " + mountPoint + path);
        if (Platform.getNativePlatform().getOS() == WINDOWS) {
            // statfs needs to be implemented on Windows in order to allow for copying
            // data from other devices because winfsp calculates the volume size based
            // on the statvfs call.
            // see https://github.com/billziss-gh/winfsp/blob/14e6b402fe3360fdebcc78868de8df27622b565f/src/dll/fuse/fuse_intf.c#L654
            if ("/".equals(path)) {
                stbuf.f_blocks.set(100 * 1024); // total data blocks in file system
                stbuf.f_frsize.set(1024);        // fs block size
                stbuf.f_bfree.set(100 * 1024);  // free blocks in fs
            }
        }
        return super.statfs(path, stbuf);
    }

    @Override
    public int rename(String path, String newName) {
        logger.log("[INFO]: rename, " + mountPoint + path);
        try{
            MemoryDirectory p = getParentDirectory(path, 0);
            String oldName = getLastComponent(path);
            p.rename(oldName, newName);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int rmdir(String path) {
        logger.log("[INFO]: rmdir, " + mountPoint + path);
        try{
            MemoryDirectory p = getParentDirectory(path, 0);
            p.delete(getLastComponent(path));
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int truncate(String path, long offset) {
        logger.log("[INFO]: truncate, " + mountPoint + path + ", " + offset);
        try{
            MemoryFile p = new MemoryFile(path, 0);
            p.truncate(offset);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int unlink(String path) {
        logger.log("[INFO]: unlink, " + mountPoint + path);
        try{
            MemoryDirectory p = getParentDirectory(path, 0);
            p.delete(getLastComponent(path));
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int link(String oldpath, String newpath) {
        logger.log("[INFO]: link, " + mountPoint + oldpath + ", " + mountPoint + newpath);
        try{
            MemoryDirectory p = getParentDirectory(newpath, 0);
            MemoryFile f = new MemoryFile(oldpath, 0);
            p.add(getLastComponent(newpath), f.id);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        // TODO: fix bug here, -o means clear all things
        logger.log("[INFO]: open, " + mountPoint + path);
        // TODO: Implement this
        return 0;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: write, " + mountPoint + path + ", " + buf + ", " + size + ", " + offset);
        try{
            MemoryFile p = new MemoryFile(path, 0);
            if (p.isDiretory()) {
                return -ErrorCodes.EISDIR();
            }
            p.write(buf, size, offset);
            p.flush();
            return 0;
        }catch (Exception e){
            return Integer.parseInt(e.getMessage());
        }
    }

//    @Override
//    public int flush(String path, FuseFileInfo fi) {
//        logger.log("[INFO]: flush, " + path + ", " + fi);
//        // TODO: this function is for Yan Lao Ge
//        return 0;
//    }
}
