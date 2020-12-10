package ru.serce.jnrfuse.proj3;


import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.Struct;
import jnr.ffi.types.*;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.NotImplemented;
import ru.serce.jnrfuse.flags.AccessConstants;
import ru.serce.jnrfuse.struct.*;
import ru.serce.jnrfuse.flags.AccessConstants.*;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;

import static jnr.ffi.Platform.OS.WINDOWS;

public class LogFS extends FuseStubFS {
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

    private class MemoryDirectory extends MemoryPath {

        private MemoryDirectory(String path) {
            super(path);
        }

        // TODO: fix the permission later
        @Override
        protected void getattr(FileStat stat) {
            stat.st_mode.set(fs.inodeOf(id).mode);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().gid.get());
        }

        public void read(Pointer buf, FuseFillDir filler) {
            DirectoryBlock directoryBlock = fs.getDirectory(id);
            for (String p : directoryBlock.contents.keySet()) {
                filler.apply(buf, p, null, 0);
            }
        }

        public void rename(String oldChildName, String newChildName) {
            DirectoryBlock data = fs.getDirectory(id);
            Integer o = data.contents.remove(oldChildName);
            data.contents.put(newChildName, o);
            updateDirectory(data);
        }

        public void delete(String childName) {
            DirectoryBlock data = fs.getDirectory(id);
            data.contents.remove(childName);
            updateDirectory(data);
        }

        public void add(String childName, int childID) {
            DirectoryBlock data = fs.getDirectory(id);
            data.contents.put(childName, childID);
            updateDirectory(data);
        }

        private void updateDirectory(DirectoryBlock data) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            data.flush(buffer);
            buffer.flip();
            fs.write(fs.inodeOf(id), buffer, 0, 1024);
        }
    }

    private class MemoryFile extends MemoryPath {
        private MemoryFile(String path) {
            super(path);
        }

        @Override
        protected void getattr(FileStat stat) {
            stat.st_mode.set(fs.inodeOf(id).mode);
            stat.st_size.set(fs.inodeOf(path).size);
            stat.st_uid.set(getContext().uid.get());
            stat.st_gid.set(getContext().gid.get());
        }

        private int read(Pointer buffer, long size, long offset) {
            Inode inode = fs.inodeOf(this.id);
            int bytesToRead = (int) Math.min(inode.size - offset, size);
            byte[] bytesRead = inode.read(fs.mem, (int) offset, (int) size).array();
            synchronized (this) {
                buffer.put(0, bytesRead, 0, bytesToRead);
            }
            return bytesToRead;
        }

        private synchronized void truncate(long size) {
            Inode inode = fs.inodeOf(this.id);
            if (size < inode.size) {
                inode.updateSize((int) size);
            }
        }

        private int write(Pointer buffer, long bufSize, long writeOffset) {
            byte[] bytesToWrite = new byte[(int) bufSize];
            synchronized (this) {
                buffer.get(writeOffset, bytesToWrite, 0, (int) bufSize);
                fs.write(fs.inodeOf(id), ByteBuffer.wrap(bytesToWrite), (int) 0, (int) bufSize);
            }
            return (int) bufSize;
        }
    }

    private abstract class MemoryPath {
        protected String name;
        protected String path;
        protected int id;

        private MemoryPath(String path) {
            this.path = path;
            this.name = getLastComponent(path);
            this.id = fs.inodeNumberOf(path);
            assert this.id != -1;
        }

        private synchronized void delete() {
            MemoryDirectory p = getParentPath(path);
            assert p != null;
            p.delete(name);
        }

        private boolean isDiretory() {
            return (fs.inodeOf(id).mode & FileStat.S_IFDIR) != 0;
        }

        protected abstract void getattr(FileStat stat);
    }

    public static void main(String[] args) {
        LogFS memfs = new LogFS();
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

    private final BaseFS fs = new BaseFS(1024 * 1024 * 100);
    private final Logger logger = new Logger();
    ;

    public LogFS() {
        selfTest();
    }

    public void selfTest() {
        // Sprinkle some files around
        createFile("/Sample file.txt", "Hello there, feel free to look around.\n");
        createDirectory("/Sample directory");
        createDirectory("/Directory with files");
        createFile("/Directory with files/hello.txt", "This is some sample text.\n");
        createFile("/Directory with files/hello again.txt", "This another file with text in it! Oh my!\n");
        createDirectory("/Directory with files/Sample nested directory");
        createFile("/Directory with files/Sample nested directory/So deep.txt",
            "Man, I'm like, so deep in this here file structure.\n");
    }

    private int createFile(String path, ByteBuffer data, Inode.Handler handler) {
        int inode = fs.createInode(data, handler);
        MemoryDirectory parent = getParentPath(path);
        assert parent != null;
        parent.add(getLastComponent(path), inode);
        return inode;
    }

    private int createFile(String path, String text) {
        try {
            return createFile(path, ByteBuffer.wrap(text.getBytes("UTF-8")), new Inode.SetMode(0777 | FileStat.S_IFREG));
        } catch (UnsupportedEncodingException e) {
            return -1;
        }
    }

    private int createDirectory(String path, long mode) {
        int inode = fs.createDirectory(mode);
        MemoryDirectory parent = getParentPath(path);
        if (parent != null) {
            parent.add(getLastComponent(path), inode);
            return 0;
        } else {
            return -ErrorCodes.ENOENT();
        }
    }

    private void createDirectory(String path) {
        createDirectory(path, 0777);
    }

    private String getLastComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private String getParentComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        assert !path.isEmpty();
        return path.substring(0, path.lastIndexOf("/"));
    }

    private MemoryDirectory getParentPath(String path) {
        MemoryPath p = getPath(getParentComponent(path));
        if (p instanceof MemoryFile)
            return null;
        else
            return (MemoryDirectory) p;
    }

    private MemoryPath getPath(String path) {
        Inode inode = fs.inodeOf(path);
        if (inode == null)
            return null;
        if ((inode.mode & FileStat.S_IFREG) != 0) {
            return new MemoryFile(path);
        } else if ((inode.mode & FileStat.S_IFDIR) != 0) {
            return new MemoryDirectory(path);
        }
        return null;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        logger.log("[INFO]: create, " + mountPoint + path);
        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        if (createFile(path, "") != -1)
            return 0;
        else
            return -ErrorCodes.ENOENT();
    }


    @Override
    public int getattr(String path, FileStat stat) {
        logger.log("[INFO]: getattr, " + mountPoint + path);
        MemoryPath p = getPath(path);
        if (p != null) {
            p.getattr(stat);
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int chmod(String path, @mode_t long mode) {
        logger.log("[INFO]: chmod, " + mode);
        Inode inode = fs.inodeOf(path);
        if (inode == null) {
            return -ErrorCodes.ENOENT();
        }
        inode.mode = mode;
        fs.update(inode.id, inode);
        return 0;
    }

    @Override
    public int chown(String path, @uid_t long uid, @gid_t long gid) {
        logger.log("[INFO]: chown, " + uid + ", " + gid);
        Inode inode = fs.inodeOf(path);
        if (inode == null) {
            return -ErrorCodes.ENOENT();
        }
        inode.gid = (int)gid;
        inode.uid = (int)uid;
        fs.update(inode.id, inode);
        return 0;
    }
    @Override
    public int access(String path, int mask) {
        logger.log("[INFO]: access, " + mask);
//        System.out.println(AccessConstants.F_OK + ", " + AccessConstants.R_OK + ", " + AccessConstants.W_OK + ", " + AccessConstants.X_OK);
        Inode inode = fs.inodeOf(path);
        if (inode == null) {
            return -ErrorCodes.ENOENT();
        }
        FuseContext context = getContext();
        int flag = 0;
        if (context.uid.intValue() == inode.uid) {
            flag |= (inode.mode >> 6) & 0x7;
        }
        if (context.gid.intValue() == inode.gid) {
            flag |= (inode.mode >> 3) & 0x7;
        }
        flag |= (inode.mode) & 0x7;
        if ((flag & mask) != mask)
            return -ErrorCodes.EACCES();
        return 0;
    }

    @Override
    public int flush(String path, FuseFileInfo fi) {
        logger.log("[INFO]: flush, " + path + ", " + fi);
        // TODO
        return 0;
    }

    @Override
    public int utimens(String path, Timespec[] timespec) {
        logger.log("[INFO]: utimens, " + path + ", " + timespec);
        // TODO
        return 0;
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        logger.log("[INFO]: mkdir, " + mountPoint + path);
        if (getPath(path) != null) {
            return -ErrorCodes.EEXIST();
        }
        return createDirectory(path, mode);
    }


    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: read, " + mountPoint + path + ", " + buf + ", " + size + ", " + offset);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        return ((MemoryFile) p).read(buf, size, offset);
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: readdir, " + mountPoint + path + ", " + buf + ", " + offset);
//        System.out.println("readdir: " + path + " " + Long.toString(offset));
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);
        ((MemoryDirectory) p).read(buf, filter);
        return 0;
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
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        MemoryDirectory newParent = getParentPath(newName);
        if (newParent == null) {
            return -ErrorCodes.ENOENT();
        }
        p.delete();
        newParent.rename(getLastComponent(path), getLastComponent(newName));
        return 0;
    }

    @Override
    public int rmdir(String path) {
        logger.log("[INFO]: rmdir, " + mountPoint + path);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryDirectory)) {
            return -ErrorCodes.ENOTDIR();
        }
        p.delete();
        return 0;
    }

    @Override
    public int truncate(String path, long offset) {
        logger.log("[INFO]: truncate, " + mountPoint + path + ", " + offset);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        ((MemoryFile) p).truncate(offset);
        return 0;
    }

    @Override
    public int unlink(String path) {
        logger.log("[INFO]: unlink, " + mountPoint + path);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        p.delete();
        return 0;
    }

    @Override
    public int link(String oldpath, String newpath) {
        logger.log("[INFO]: link, " + mountPoint + oldpath + ", " + mountPoint + newpath);
        //TODO: implement hard links
        return super.link(oldpath, newpath);
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        logger.log("[INFO]: open, " + mountPoint + path);
//        MemoryPath p = getPath(path);
//        if (p == null) {
//            return -ErrorCodes.ENOENT();
//        }
        return 0;
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        logger.log("[INFO]: write, " + mountPoint + path + ", " + buf + ", " + size + ", " + offset);
        MemoryPath p = getPath(path);
        if (p == null) {
            return -ErrorCodes.ENOENT();
        }
        if (!(p instanceof MemoryFile)) {
            return -ErrorCodes.EISDIR();
        }
        return ((MemoryFile) p).write(buf, size, offset);
    }
}
