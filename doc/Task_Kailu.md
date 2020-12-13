### Main Data Structures Of File System

* As described in paper of LFS, we have different types of data blocks. 
They are `Checkpoint`, `InodeMap`, `DataBlock`, `DirectoryBlock`, `Inode`, `Level1IndirectBlock`, and `Level2IndirectBlock`. They are all extended from `WritableObject` that have function `flush()` and `parse()`, which allow to turn them into bytes or parse from bytes.
* The metadata in stored in `Inode`, and the `Inode` object allows users to read or write data organized by this inode through `Inode.read()`, `Inode.write()`.  
* Build on these, we implement a LFS file system that can interact with the disk which is handling all reads, writes and changes for inode and data. To fast query address from inode, a large HashMap is used to map inode to physical address. Class `Logger` is designed to record all operations. Class `Tester` provide convenient ways to self-test. Class `MemoryManager` is designed to handle all read and writes to cache.
* For better OOP, we create class `MemoryFile` and `MemoryDirectory`, which can be regard as normal file and directory. For example, create a subdirectory in a directory only needs `parentDirectory.createDirectory(childName)`. 
* Only using interface from `MemoryFile` and `MemoryDirectory`, we implement functions from `jnr-fuse`, including `getattr`, `chmod`, `chown`, `utimens`, `access`, `create`, `mkdir`, `read`, `readdir`, `statfs`, `rename`, `rmdir`, `truncate`, `unlink`, `link`, `open`, `write`, `flush`, `fsync`, and `fsyncdir`.

### Code organization

In `src/main/java/ru.serce.jnrfuse/proj3`, 

* `Checkpoint.java` contains `Checkpoint`.
* `DataBlock.java` contains `DataBlock`.
* `InodeMap.java` contains `InodeMap`.
* `DirectoryBlock.java` contains `DirectoryBlock`.
* `Inode.java` contains `Inode`.
* `Level1IndirectBlock.java` contains `Level1IndirectBlock`.
* `Level2IndirectBlock.java` contains `Level2IndirectBlock`.
* `WritableObject.java` contains `WritableObject`.
* `LFS.java` contain the main class `LogFS`, and `Logger`, `MemoryManager`, `Tester`, `MemoryFile`, `MemoryDirectory` are subclass of it. The functions from `jnr-fuse` are here as well.

### A User Manual for Non-standard Operations

* There's no non-standard operations here.

### Known bugs and limitations

* So far, there's no known bugs.
