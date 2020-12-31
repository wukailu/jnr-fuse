package ru.serce.jnrfuse.proj3;


import jnr.constants.platform.OpenFlags;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.*;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.flags.AccessConstants;
import ru.serce.jnrfuse.struct.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

import java.io.*;
import java.util.concurrent.locks.*;

import static jnr.ffi.Platform.OS.WINDOWS;

public class LogFS extends FuseStubFS {
    MemoryManager manager;
    private int inodeCnt;
    /* The total size of FS, counted in Byte */
    private final int total_size;

    private Checkpoint checkpoint1, checkpoint2, checkpoint;
    private Map<Integer, Integer> oldInodeMap, newInodeMap;

    public final FileLock fileLock = new FileLock();
    private Lock lock;

    LogFS(int total_size){
        this(ByteBuffer.allocate(total_size));
        lock = new ReentrantLock();
    }

    // Load from existing mem
    LogFS(ByteBuffer mem){
        this.total_size = mem.capacity();
        checkpoint1 = new Checkpoint();
        checkpoint2 = new Checkpoint();
        checkpoint1.parse(mem, this.total_size-2048, 1024);
        checkpoint2.parse(mem, this.total_size-1024, 1024);
        if (checkpoint1.valid())
            checkpoint = checkpoint1;
        else
            checkpoint = checkpoint2;
        this.manager = new MemoryManager(mem, checkpoint.lastInodeMap + 1024);
        oldInodeMap = new HashMap<Integer, Integer>();
        newInodeMap = new HashMap<Integer, Integer>();
        inodeCnt = 0;
        int lastInodeMap = checkpoint.lastInodeMap;
//        System.out.println(total_size);
//        System.out.println(lastInodeMap);
        List<InodeMap> f = new ArrayList<>();
        while (lastInodeMap > 0){
            InodeMap inodeMap = new InodeMap().parse(mem, lastInodeMap, 1024);
            f.add(inodeMap);
            lastInodeMap = inodeMap.preInodeMapAddress;
        }
        Collections.reverse(f);
        for(InodeMap m: f)
            oldInodeMap.putAll(m.inodeMap);
        for(int i: oldInodeMap.keySet())
            inodeCnt = Math.max(inodeCnt, i);
        if (inodeCnt == 0){ // create "/"
            createDirectory(0777, Inode.Identity);
        }
        lock = new ReentrantLock();
    }



    static class MemoryManager{
        // TODO: YSY- Replace with a write-back memory cache, 4MB only, use any cache replacement policy.
        // TODO: YSY- To support garbage collection, add a release function to mark a block as garbage.
        // TODO: YSY- Check if this part is thread safe
        // TODO: YSY- To implement garbage collection, we use two thread safe FIFO record all the free block address.
        //  One only record old free space FIFO, one record real state.

        public void sync(){

        }

        public void release(int address){

        }

        private ByteBuffer mem;
        private int mark;
        MemoryManager(ByteBuffer mem, int mark){
            this.mem = mem;
            this.mark = mark;
        }

        public int write(ByteBuffer data){
            int ret = mark;
            mem.position(mark);
            mem.put(data);
            if(mem.position()%1024 != 0)
                mark = (mem.position()/1024 + 1) * 1024;
            else
                mark = mem.position();
            return ret;
        }

        // This is for checkpoint
        public int write_at(ByteBuffer data, int startAddress){
            mem.position(startAddress);
            mem.put(data);
            return startAddress;
        }

        public ByteBuffer read(int startAddress, int len){
            assert len % 1024 == 0;
            byte[] ret = new byte[len];
            mem.position(startAddress);
            mem.get(ret, 0, len);
            return ByteBuffer.wrap(ret);
        }

        public byte[] toBytes(){
            return mem.array();
        }

        public int getMark(){
            return mark;
        }
    }


    private static ByteBuffer readFromDisk(String s)
    {
        File file = new File(s);
        if (!file.exists())
        {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        byte[] x = new byte[1024*1024*100];
        try {
            FileInputStream in = new FileInputStream(file);
            try {
                in.read(x,0,1024*1024*100);
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        ByteBuffer y = ByteBuffer.wrap(x);
        return y;
    }

    private int updateInodeMap()
    {
        int p = checkpoint.lastInodeMap, q = p + 1024;
        InodeMap m = new InodeMap();
        m.preInodeMapAddress = p;
        for (Map.Entry<Integer, Integer> e : newInodeMap.entrySet())
        {
            m.inodeMap.put(e.getKey(), e.getValue());
            if (m.size() == 127)
            {
                p = manager.write(m.flush());
                m = new InodeMap();
                m.preInodeMapAddress = p;
            }
        }
        if (m.size() > 0)
            p = manager.write(m.flush());
        oldInodeMap.putAll(newInodeMap);
        newInodeMap = new HashMap<Integer, Integer>();
        checkpoint.update(p);
        manager.write_at(checkpoint.flush(), total_size-2048);
        manager.write_at(checkpoint.flush(), total_size-1024);
//        System.out.println(checkpoint.lastInodeMap);
        return q;
    }

    private void writeToDisk(String s)
    {
        if (newInodeMap.isEmpty())
            return;
        int p = updateInodeMap();
        File file = new File(s);
        if (!file.exists())
        {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        byte[] x = manager.toBytes();
        try {
            RandomAccessFile out = new RandomAccessFile(file, "rw");
            try {
                out.seek(p);
                out.write(x,p,manager.getMark()-p);
                out.seek(total_size-2048);
                out.write(x,total_size-2048,2048);
                //out.write(x,0,1024*1024*100);
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
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
        Inode.Handler newHandler = Inode.Sequential(
            handler,
            Inode.SetOwner(getContext().uid.intValue(), getContext().gid.intValue()),
            Inode.SetTimestamp("a"),
            Inode.SetTimestamp("m"),
            Inode.SetTimestamp("c")
        );
        newInode = newHandler.process(handler.process(newInode));
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
        Inode.Handler newHandler = Inode.Sequential(
            handler,
            Inode.SetMode((int) mode | FileStat.S_IFDIR)
        );
        return createInode(new DirectoryBlock().flush(), newHandler);
    }

    /***
     * Update an inode. Write it to disk and update the inodeMap.
     * @param node      The new Inode.
     * @return          The new address on disk where it write to.
     */
    private synchronized int update(Inode node){
        int ret = manager.write(node.flush());
        newInodeMap.put(node.id, ret);
        return ret;
    }


    private boolean checkPrivilege(Inode inode, int mask) {
        int flag = (int) (inode.mode & 0x7);
        FuseContext context = getContext();
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
     * @return  The inode number of this. -1 for invalid path.
     */
    private int inodeIdOf(String path) throws Exception {
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.equals(""))
            return 1; // the file number of "/" is 1
        String[] pathList = path.split("/");
        int currentInode = 1;
        for (String s: pathList) {
            fileLock.acquireReadLock(currentInode);
            Inode inode = inodeOf(currentInode);
            if ((inode.mode & FileStat.S_IFDIR) == 0) {
                fileLock.releaseReadLock(currentInode);
                throw new Exception(String.valueOf(-ErrorCodes.ENOENT()));
            }
            if (!checkPrivilege(inode, AccessConstants.X_OK)) {
                fileLock.releaseReadLock(currentInode);
                throw new Exception(String.valueOf(-ErrorCodes.EACCES()));
            }
            DirectoryBlock directory = new DirectoryBlock().parse(inode.read(manager, 0, inode.space));
            Object ret = directory.contents.get(s);
            if(ret == null) {
                fileLock.releaseReadLock(currentInode);
                throw new Exception(String.valueOf(-ErrorCodes.ENOENT()));
            }
            fileLock.releaseReadLock(currentInode);
            currentInode = (int) ret;
        }
        return currentInode;
    }

    // TODO: Kailu- Add inode address into inode, whenever update a inode, delete the old one.
    /***
     * The Inode Object of a inode id. you must obtain a read lock before calling this function!!
     * @param id Inode ID.
     * @return  The Inode.
     */
    private Inode inodeOf(int id) throws Exception {
        return new Inode(id).parse(manager.read(getInodeAddress(id), 1024));
    }

    /***
     * Read the dataBlock of the inode, and parse into a Directory Block.
     * @param node   The inode of Directory.
     * @return  A DirectoryBlock.
     */
    private DirectoryBlock getDirectory(Inode node) throws Exception {
        if ((node.mode & FileStat.S_IFDIR) != 0)
            return new DirectoryBlock().parse(read(node, 0, node.space));
        else
            throw new Exception(String.valueOf(-ErrorCodes.ENOTDIR()));
    }

    /***
     * Read dataBlock in the inode.
     * @param node          The inode.
     * @param readOffset    The offset for the file. 0 means read from the start of the file.
     * @param size          The size needed to read.
     * @return              A ByteBuffer contain the data.
     */
    private ByteBuffer read(Inode node, int readOffset, int size) {
        return node.read(manager, readOffset, size);
    }

    /***
     * Write data to an inode, the data and the modified inode will both write to the disk.
     * @param node          The inode.
     * @param buffer        The buffer containing data needed to write. Write from the buffer.position().
     * @param writeOffset   The offset of the write. 0 means write from the beginning of the file.
     * @param size          The size of data need to write to disk in buffer.
     */
    private synchronized void write(Inode node, ByteBuffer buffer, int writeOffset, int size) {
        node.write(buffer, manager, writeOffset, size, false);
        update(node);
    }

    //------

    private class FileLock {
        private final Map<Integer, ReadWriteLock> fileLockMap = new HashMap<Integer, ReadWriteLock>();

        public void acquireReadLock(int id) {
            System.out.println("[DEBUG] accquire read lock " + id + " begin");
            Lock rlock = getReadLock(id);
            rlock.lock();
            System.out.println("[DEBUG] accquire read lock " + id + " end");
        }
        public void releaseReadLock(int id) {
            System.out.println("[DEBUG] release read lock " + id + " begin");
            Lock rlock = getReadLock(id);
            rlock.unlock();
            System.out.println("[DEBUG] release read lock " + id + " end");
        }
        public void acquireWriteLock(int id) {
            System.out.println("[DEBUG] accquire write lock " + id + " begin");
            Lock wlock = getWriteLock(id);
            wlock.lock();
            System.out.println("[DEBUG] accquire write lock " + id + " end");
        }
        public void releaseWriteLock(int id) {
            System.out.println("[DEBUG] release write lock " + id + " begin");
            Lock wlock = getWriteLock(id);
            wlock.unlock();
            System.out.println("[DEBUG] release write lock " + id + " end");
        }
        private synchronized ReadWriteLock getReadWriteLock(int id) {
            ReadWriteLock rwlock = fileLockMap.get(id);
            if (rwlock == null) {
                rwlock = new ReentrantReadWriteLock();
                fileLockMap.put(id, rwlock);
            }
            return rwlock;
        }
        private synchronized Lock getReadLock(int id) {
            return getReadWriteLock(id).readLock();
        }
        private synchronized Lock getWriteLock(int id) {
            return getReadWriteLock(id).writeLock();
        }
    }

    private class MemoryDirectory extends MemoryFile {
        private DirectoryBlock data;
        /***
         * In order to make this thread safe, don't storage the MemoryPath!!! Allocate it every time!!!
         * @param path Path of this.
         * @throws Exception If there's error will return the error value as a String.
         */
        protected MemoryDirectory(String path, int lockLevel) throws Exception {
            super(path, lockLevel);
            data = getDirectory(inode);
        }
        protected MemoryDirectory(int inodeID, int lockLevel) throws Exception {
            super(inodeID, lockLevel);
            data = getDirectory(inode);
        }

        protected MemoryDirectory(MemoryFile file) throws Exception {
            super(file);
            data = getDirectory(inode);
        }

        protected int read(Pointer buf, FuseFillDir filler) throws Exception {
            int ret = 0;
            for (String p : data.contents.keySet()) {
                ret += filler.apply(buf, p, null, 0);
            }
            return ret;
        }

        protected void rename(String oldChildName, String newChildName){
            Integer o = data.contents.remove(oldChildName);
            data.contents.put(newChildName, o);
        }

        protected Integer delete(String childName){
            return data.contents.remove(childName);
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

        protected boolean hasChild(String childName){
            return data.contents.containsKey(childName);
        }

        protected boolean isEmpty(){
            return data.contents.isEmpty();
        }

        @Override
        protected void flush(){
            if (data != null){
                inode.truncate(manager, 0);
                ByteBuffer buffer = data.flush();
                LogFS.this.write(inode, buffer, 0, buffer.remaining());
            }
            super.flush();
        }
    }

    private class MemoryFile {
        protected int id;
        protected Inode inode;
        protected int lockLevel;
        private boolean acquired = false;

        /***
         * In order to make this thread safe, don't storage the MemoryPath!!! Allocate it every time!!!
         * @param path Path of this.
         * @throws Exception If there's error will return the error value as a String.
         */
        protected MemoryFile(String path, int lockLevel) throws Exception {
            this(inodeIdOf(path), lockLevel);
        }

        protected MemoryFile(int inodeID, int lockLevel) throws Exception {
            this.id = inodeID;
            this.lockLevel = lockLevel;
            acquire();
            this.inode = inodeOf(inodeID);
        }

        protected MemoryFile(MemoryFile file) {
            this.id = file.id;
            this.lockLevel = file.lockLevel;
            this.acquired = file.acquired;
            this.inode = file.inode;
        }

        // TODO: ChenTong- Add a 'require lock' funcion and a 'release lock' function and use them in functions blow

        public void acquire() {
            if (this.lockLevel == 0)
                fileLock.acquireReadLock(this.id);
            else
                fileLock.acquireWriteLock(this.id);
            acquired = true;
        }
        public void release() {
            if (this.lockLevel == 0)
                fileLock.releaseReadLock(this.id);
            else
                fileLock.releaseWriteLock(this.id);
            acquired = false;
        }

        public void close() {
            if (acquired == true) {
                release();
                acquired = false;
            }
        }

        protected MemoryDirectory toDirectory() throws Exception {
            return new MemoryDirectory(this);
        }

        protected boolean isDirectory() {
            return (inode.mode & FileStat.S_IFDIR) != 0;
        }

        protected void getattrCommon(FileStat stat) {
            stat.st_mode.set(inode.mode);
            stat.st_uid.set(inode.uid);
            stat.st_gid.set(inode.gid);
            stat.st_nlink.set(inode.hardLinks);

            stat.st_ctim.tv_sec.set(inode.lastChangeTime / 1000);
            stat.st_atim.tv_sec.set(inode.lastAccessTime / 1000);
            stat.st_mtim.tv_sec.set(inode.lastModifyTime / 1000);
        }

        protected void getattr(FileStat stat) {
            getattrCommon(stat);
            if (!isDirectory())
                stat.st_size.set(inode.size);
        }

        protected boolean access(int mask) {
            assert 0 <= mask && mask <= 7: "mask is invalid";
            if (inode == null)
                return false;
            return checkPrivilege(inode, mask);
        }

        protected int read(Pointer buffer, long size, long offset) {
            int bytesToRead = (int) Math.min(inode.size - offset, size);
            byte[] bytesRead = inode.read(manager, (int) offset, bytesToRead).array();
            synchronized (this) {
                buffer.put(0, bytesRead, 0, bytesToRead);
            }
            return bytesToRead;
        }

        protected synchronized void truncate(int size) {
            if (size < inode.size) {
                inode.truncate(manager, size);
            }
        }

        protected int write(Pointer buffer, long bufSize, long writeOffset) {
            byte[] bytesToWrite = new byte[(int) bufSize];
            synchronized (this) {
                buffer.get(0, bytesToWrite, 0, (int) bufSize);
                LogFS.this.write(inode, ByteBuffer.wrap(bytesToWrite), (int) writeOffset, (int) bufSize);
            }
            return (int) bufSize;
        }

        protected void flush(){
            if (inode != null){
                update(inode);
            }
        }
    }

    private MemoryDirectory getParentDirectory(String path, int lockLevel) throws Exception {
        return new MemoryDirectory(getParentComponent(path), lockLevel);
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

    private final Logger logger = new Logger(0);

    private class block_dump
    {
        private void pretty_print()
        {
            operationBegin();
            Map<Integer, Integer> m = oldInodeMap;
            m.putAll(newInodeMap);
            for (Integer x : m.values())
            {
                Inode i = new Inode(0).parse(manager.read(x, 1024));
                i.pretty_print();
            }
            operationEnd();
        }
    }

    public void pretty_print()
    {
        block_dump d = new block_dump();
        d.pretty_print();
    }

    public static void main(String[] args) {
        ByteBuffer x = readFromDisk("LFS");
        LogFS memfs = new LogFS(x);
        //memfs.pretty_print();
        //memfs.selfTest();
        //memfs.selfTest2();
        //memfs.writeToDisk("LFS");
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
            MemoryDirectory directory = getParentDirectory(path, 1);
            directory.createFile(getLastComponent(path), ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)), Inode.SetMode(0777 | FileStat.S_IFREG));
            directory.flush();
        }

        private void createDirectory(String path) throws Exception {
            MemoryDirectory directory = new MemoryDirectory(getParentComponent(path), 1);
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

    public void selfTest2() {
        Tester tester = new Tester();
        try{
            tester.createFile("/a.txt", "Hello.\n");
        } catch (Exception e){
            logger.log("Self Test 2 Failed!!!");
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

    private void operationBegin()
    {
//        lock.lock();
    }

    private void operationEnd()
    {
        if (manager.getMark() - checkpoint.lastInodeMap > 1024 * 1024)
            writeToDisk("LFS");
//        lock.unlock();
    }

    public void close(MemoryFile p) {
        if (p != null)
            p.close();
    }

    @Override
    public int getattr(String path, FileStat stat) {
        operationBegin();
        logger.log("[INFO]: getattr, " + mountPoint + path);
        MemoryFile p = null;
        try{
            p = new MemoryFile(path, 0);
            if (p.isDirectory())
                p = p.toDirectory();
            p.getattr(stat);
            close(p);
            operationEnd();
            return 0;
        }catch (Exception e) {
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int chmod(String path, @mode_t long mode) {
        operationBegin();
        logger.log("[INFO]: chmod, " + path + ", " + mode);
        assert 0 <= mode && mode <= 0777: "mode is not a valid format";
        MemoryFile p = null;
        try {
            p = new MemoryFile(path, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            p.inode.mode = mode;
            p.flush();
            close(p);
            operationEnd();
            return 0;
        } catch (Exception e) {
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int chown(String path, @uid_t long uid, @gid_t long gid) {
        operationBegin();
        logger.log("[INFO]: chown, " + path + ", " + uid + ", " + gid);
        assert -1 <= (int)uid && (int)uid <= 65535: "uid is not valid";
        assert -1 <= (int)gid && (int)gid <= 65535: "gid is not valid";
        MemoryFile p = null;
        try{
            p = new MemoryFile(path, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            if ((int) uid != -1)
                p.inode.uid = (int) uid;
            if ((int) gid != -1)
                p.inode.gid = (int) gid;
            p.flush();
            close(p);
            operationEnd();
            return 0;
        } catch (Exception e) {
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        operationBegin();
        logger.log("[INFO]: utimens, " + path + ", " + timespec);
        assert timespec.length == 2 : "the length of argument timespec is not 2.";
        MemoryFile p = null;
        try {
            p = new MemoryFile(path, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            p.inode.lastAccessTime = timespec[0].tv_sec.longValue() * 1000;
            p.inode.lastModifyTime = timespec[1].tv_sec.longValue() * 1000;
            p.flush();
            close(p);
            operationEnd();
            return 0;
        } catch (Exception e) {
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int access(String path, int mask) {
        operationBegin();
        logger.log("[INFO]: access, " + path + ", " + mask);
//        System.out.println("[INFO] UIDGID: " + getContext().uid.intValue() + "  " + getContext().gid.intValue());
//        System.out.println(AccessConstants.F_OK + ", " + AccessConstants.R_OK + ", " + AccessConstants.W_OK + ", " + AccessConstants.X_OK);
        MemoryFile p = null;
        try{
            p = new MemoryFile(path, 0); // check permission
            if (!p.access(mask))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            close(p);
            operationEnd();
            return 0;
        } catch (Exception e) {
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    public int create_(String path, @mode_t long mode, FuseFileInfo fi) {
        MemoryDirectory p = null;
        try{
            p = getParentDirectory(path, 1);
            if (!p.access(AccessConstants.W_OK)) {
                close(p);
                return -ErrorCodes.EACCES();
            }
            p.createFile(getLastComponent(path), ByteBuffer.allocate(0), Inode.SetMode((int) mode));
            p.flush();
            close(p);
            return 0;
        }catch (Exception e){
            close(p);
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        operationBegin();
        logger.log("[INFO]: create, " + mountPoint + path);
        int flag = create_(path, mode, fi);
        operationEnd();
        return flag;
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        operationBegin();
        logger.log("[INFO]: mkdir, " + mountPoint + path);
        MemoryDirectory p = null;
        try{
            p = getParentDirectory(path, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            p.createDirectory(getLastComponent(path), mode, Inode.Identity);
            p.flush();
            close(p);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        operationBegin();
        logger.log("[INFO]: read, " + mountPoint + path + ", " + buf + ", " + size + ", " + offset);
        MemoryFile p = null;
        try {
            p = new MemoryFile(path, 1);
            if (!p.access(AccessConstants.R_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            if (p.isDirectory()) {
                close(p);
                operationEnd();
                return -ErrorCodes.EISDIR();
            }
            int ret = p.read(buf, size, offset);
            p.flush();
            close(p);
            operationEnd();
            return ret;
        } catch (Exception e) {
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: readdir, " + mountPoint + path + ", " + buf + ", " + offset);
        MemoryDirectory p = null;
        try{
            p = new MemoryDirectory(path, 0);
            if (!p.access(AccessConstants.R_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            int ret = 0;
            ret += filter.apply(buf, ".", null, 0);
            ret += filter.apply(buf, "..", null, 0);
            ret += p.read(buf, filter);
            if (ret != 0)
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EFBIG();
            }
            close(p);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        // TODO: do we need operation end?
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
    public int rename(String path, String newPath) {
        operationBegin();
        logger.log("[INFO]: rename, " + mountPoint + " -> " + newPath);
        MemoryDirectory p = null, t = null;
        try{
            if (getParentComponent(newPath).equals(getParentComponent(path))){
                p = getParentDirectory(path, 1);
                if (!p.access(AccessConstants.W_OK))
                {
                    close(p);
                    operationEnd();
                    return -ErrorCodes.EACCES();
                }
                String oldName = getLastComponent(path);
                p.rename(oldName, getLastComponent(newPath));
                p.flush();
                close(p);
            }else{
                int pid = inodeIdOf(getParentComponent(path)), tid = inodeIdOf(getParentComponent(newPath));
                p = new MemoryDirectory(pid, 1);
                t = new MemoryDirectory(tid, 1);
                Integer o = p.delete(getLastComponent(path));
                t.add(getLastComponent(newPath), o);
                p.flush();
                t.flush();
                close(p);
                close(t);
            }
            operationEnd();
            return 0;
        } catch (Exception e) {
            close(p);
            close(t);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int rmdir(String path) {
        operationBegin();
        logger.log("[INFO]: rmdir, " + mountPoint + path);
        MemoryDirectory p = null, f = null;
        try{
            int pid = inodeIdOf(getParentComponent(path)), fid = inodeIdOf(path);
            p = new MemoryDirectory(pid, 1);
            f = new MemoryDirectory(fid, 1);
            if (!p.access(AccessConstants.W_OK) || !f.access(AccessConstants.W_OK)){
                close(p);
                close(f);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            if (!f.isEmpty()){
                close(p);
                close(f);
                operationEnd();
                return -ErrorCodes.ENOTEMPTY();
            }
            p.delete(getLastComponent(path));
            p.flush();
            close(p);
            close(f);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            close(f);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    private int truncate_(String path, long offset) {
        operationBegin();
        MemoryFile p = null;
        try{
            p = new MemoryFile(path, 1);
            if (!p.access(AccessConstants.W_OK)) {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            p.truncate((int) offset);
            p.flush();
            close(p);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int truncate(String path, long offset) {
        operationBegin();
        logger.log("[INFO]: truncate, " + mountPoint + path + ", " + offset);
        int flag = truncate_(path, offset);
        operationEnd();
        return flag;
    }

    @Override
    public int unlink(String path) {
        operationBegin();
        logger.log("[INFO]: unlink, " + mountPoint + path);
        MemoryDirectory p = null;
        MemoryFile f = null;
        try{
            int pid = inodeIdOf(getParentComponent(path)), fid = inodeIdOf(path);
            p = new MemoryDirectory(pid, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            int inode = p.delete(getLastComponent(path));
            p.flush();
            close(p);
            f = new MemoryFile(fid, 1);
            f.inode.hardLinks -= 1;
            // TODO: if the number of hardlinks is zero, then delete this file
            f.flush();
            close(f);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            close(f);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int link(String oldpath, String newpath) {
        operationBegin();
        logger.log("[INFO]: link, " + mountPoint + oldpath + ", " + mountPoint + newpath);
        MemoryDirectory p = null;
        MemoryFile f = null;
        try{
            int pid = inodeIdOf(getParentComponent(newpath)), fid = inodeIdOf(oldpath);
            p = new MemoryDirectory(pid, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            if(p.hasChild(getLastComponent(newpath)))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EEXIST();
            }
            f = new MemoryFile(fid, 1);
            f.inode.hardLinks += 1;
            f.flush();
            p.add(getLastComponent(newpath), f.id);
            p.flush();
            close(p);
            close(f);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            close(f);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        operationBegin();
        logger.log("[INFO]: open, " + mountPoint + path);
        System.out.println("[INFO]: open " + fi.flags.intValue());
        System.out.println(OpenFlags.O_RDONLY.intValue());
        System.out.println(OpenFlags.O_WRONLY.intValue());
        System.out.println(OpenFlags.O_RDWR.intValue());
        System.out.println(OpenFlags.O_APPEND.intValue());
        System.out.println(OpenFlags.O_EXCL.intValue());

        MemoryFile p = null, q = null;
        try{
            if ((fi.flags.intValue() & OpenFlags.O_TRUNC.intValue()) != 0){
                int ret = truncate_(path, 0);
                if (ret != 0)
                {
                    operationEnd();
                    return ret;
                }
            }
            p = new MemoryFile(path, 1);
            if (p.isDirectory()) {
                close(p);
                operationEnd();
                return -ErrorCodes.EISDIR();
            }
            fi.fh.set(p.id);
            p.flush();
            close(p);
            operationEnd();
            return 0;
        }catch (Exception e){
            close(p);
            int error = Integer.parseInt(e.getMessage());
            if (error == ErrorCodes.ENOENT() && (fi.flags.intValue() & OpenFlags.O_CREAT.intValue()) != 0){
                int ret = create_(path, 0644, fi);
                if (ret != 0)
                {
                    operationEnd();
                    return ret;
                }
                try{ //TODO: what it is?
                    q = new MemoryFile(path, 1);
                    if (q.isDirectory()) {
                        close(q);
                        operationEnd();
                        return -ErrorCodes.EISDIR();
                    }
                    fi.fh.set(q.id);
                    close(q);
                    operationEnd();
                    return 0;
                }catch (Exception e2){
                    close(q);
                    operationEnd();
                    return Integer.parseInt(e2.getMessage());
                }
            }
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        operationBegin();
        logger.log("[INFO]: write, " + mountPoint + path + ", " + buf + ", " + size + ", " + offset);
        MemoryFile p = null;
        try{
            p = new MemoryFile(path, 1);
            if (!p.access(AccessConstants.W_OK))
            {
                close(p);
                operationEnd();
                return -ErrorCodes.EACCES();
            }
            if (p.isDirectory()) {
                close(p);
                operationEnd();
                return -ErrorCodes.EISDIR();
            }
            int ret = p.write(buf, size, offset);
            p.flush();
            close(p);
            operationEnd();
            return ret;
        }catch (Exception e){
            close(p);
            operationEnd();
            return Integer.parseInt(e.getMessage());
        }
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        // TODO: do we need operation end?
        logger.log("[INFO]: flush, " + path + ", " + fi);
        return 0;
    }

    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        logger.log("[INFO]: fsync, " + path + ", " + isdatasync + ", " + fi);
        writeToDisk("LFS");
        return 0;
    }

    @Override
    public int fsyncdir(String path, FuseFileInfo fi) {
        logger.log("[INFO]: fsyncdir, " + path + ", " + fi);
        writeToDisk("LFS");
        return 0;
    }
}
