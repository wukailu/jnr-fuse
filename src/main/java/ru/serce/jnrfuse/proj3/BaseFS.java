package ru.serce.jnrfuse.proj3;

import ru.serce.jnrfuse.struct.FileStat;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

// The first block is not used.
public class BaseFS {
    protected ByteBuffer mem;
    protected int mark;
    protected int inodeCnt;
    /* The total size of FS, counted in Byte */
    protected int total_size;

    public Checkpoint checkpoint1, checkpoint2, checkpoint;
    public Map<Integer, Integer> oldInodeMap, newInodeMap;

    BaseFS(int total_size){
        this(ByteBuffer.allocate(total_size));
    }

    // Load from existing mem
    BaseFS(ByteBuffer mem){
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
            createDirectory(0777);
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
    public int getInodeAddress(int inode){
        Object ret = newInodeMap.get(inode);
        if (ret == null)
            ret = oldInodeMap.get(inode);
        assert ret != null;
        return (int) ret;
    }

    /***
     * Create an inode and write data in dataBuffer in this inode.
     * @param dataBuffer    The data that need to write to disk, the new inode is point to this data.
     * @param handler       The handler for inode, e.g. modify mode or some value else.
     * @return              The inode number of the new inode
     */
    public int createInode(ByteBuffer dataBuffer, Inode.Handler handler){
        int inodeNumber = ++inodeCnt;
        Inode newInode = new Inode(inodeNumber);
        newInode = handler.process(newInode);
        write(newInode, dataBuffer, 0, dataBuffer.remaining());
        return inodeNumber;
    }

    /***
     * Create a directory with mode.
     * @param mode  mode of directory. Check FileStat to understand meaning.
     * @return  Return the inode number of the directory.
     */
    public synchronized int createDirectory(long mode){
        ByteBuffer dataBuffer = ByteBuffer.allocate(1024);
        DirectoryBlock directoryBlock = new DirectoryBlock();
        directoryBlock.flush(dataBuffer);
        dataBuffer.flip();
        Inode.Handler handler = new Inode.SetAll(
            new Inode.SetMode((int) mode | FileStat.S_IFDIR)
        );
        return createInode(dataBuffer, handler);
    }

    // return the block address where it written to

    /***
     * Update an inode. Write it to disk and update the inodeMap.
     * @param inode The id of the inode.
     * @param obj   The new Inode.
     * @return  The new address on disk where it write to.
     */
    public synchronized int update(int inode, Inode obj){
        int ret = mark;
        reset();
        obj.flush(mem);
        mark(mem.position());
        newInodeMap.put(inode, ret);
        return ret;
    }


    /***
     * The inode number of a file or directory.
     * @param path  The path, in Linux form.
     * @return  The inode number of this. -1 for invalid path.
     */
    public int inodeNumberOf(String path){
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.equals(""))
            return 1;
        String[] pathList = path.split("/");
        int currentInode = 1;
        for (String s: pathList){
            Inode inode = inodeOf(currentInode);
            if ((inode.mode & FileStat.S_IFDIR) == 0){
                return -1;
            }
            //TODO: check permission here
            DirectoryBlock directory = new DirectoryBlock().parse(inode.read(mem, 0, 1024), 0, 1024);
            Object ret = directory.contents.get(s);
            if(ret == null)
                return -1;
            currentInode = (int) ret;
        }
        return currentInode;
    }

    /***
     * The Inode Object of a path.
     * @param path  A path. In Linux form.
     * @return  The Inode if path is valid else return null.
     */
    public Inode inodeOf(String path){
        int inode = inodeNumberOf(path);
        if (inode != -1)
            return inodeOf(inode);
        else
            return null;
    }

    /***
     * The Inode Object of a inode id.
     * @param id Inode ID.
     * @return  The Inode.
     */
    public Inode inodeOf(int id){
        return new Inode(id).parse(mem, getInodeAddress(id), 1024);
    }

    /***
     * Read the dataBlock of the inode, and parse into a Directory Block.
     * @param id    Inode ID.
     * @return  A DirectoryBlock.
     */
    public DirectoryBlock getDirectory(int id){
        if (id != -1){
            Inode node = inodeOf(id);
            if ((node.mode & FileStat.S_IFDIR) != 0)
                return new DirectoryBlock().parse(read(node, 0, 1024));
        }
        return null;
    }

    /***
     * Read dataBlock in the inode.
     * @param node          The inode.
     * @param readOffset    The offset for the file. 0 means read from the start of the file.
     * @param size          The size needed to read.
     * @return              A ByteBuffer contain the data.
     */
    public ByteBuffer read(Inode node, int readOffset, int size) {
        return node.read(mem, readOffset, size);
    }

    /***
     * Write data to an inode, the data and the modified inode will both write to the disk.
     * @param node          The inode.
     * @param buffer        The buffer containing data needed to write. Write from the buffer.position().
     * @param writeOffset   The offset of the write. 0 means write from the beginning of the file.
     * @param size          The size of data need to write to disk in buffer.
     */
    public synchronized void write(Inode node, ByteBuffer buffer, int writeOffset, int size) {
        reset();
        node.write(buffer, mem, writeOffset, size);
        mark(mem.position());
        update(node.id, node);
    }
}
