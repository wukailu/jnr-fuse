# Project 4: Log File System
> Group Member: 陈通, 吴凯路, 闫书弈
>
> Github repo: https://github.com/wukailu/jnr-fuse
>
> Github invitation link: https://github.com/wukailu/jnr-fuse/invitations

## 1) Main Data Structures Of File System

* As described in paper of LFS, we have different types of data blocks. 
They are `Checkpoint`, `InodeMap`, `DataBlock`, `DirectoryBlock`, `Inode`, `Level1IndirectBlock`, and `Level2IndirectBlock`. They are all extended from `WritableObject` that have function `flush()` and `parse()`, which allow to turn them into bytes or parse from bytes.
* The metadata in stored in `Inode`, and the `Inode` object allows users to read or write data organized by this inode through `Inode.read()`, `Inode.write()`.  
* Build on these, we implement a LFS file system that can interact with the disk which is handling all reads, writes and changes for inode and data. To fast query address from inode, a large HashMap is used to map inode to physical address. Class `Logger` is designed to record all operations. Class `Tester` provide convenient ways to self-test. Class `MemoryManager` is designed to handle all read and writes to cache.
* For better OOP, we create class `MemoryFile` and `MemoryDirectory`, which can be regard as normal file and directory. For example, create a subdirectory in a directory only needs `parentDirectory.createDirectory(childName)`. 
* Only using interface from `MemoryFile` and `MemoryDirectory`, we implement functions from `jnr-fuse`, including `getattr`, `chmod`, `chown`, `utimens`, `access`, `create`, `mkdir`, `read`, `readdir`, `statfs`, `rename`, `rmdir`, `truncate`, `unlink`, `link`, `open`, `write`, `flush`, `fsync`, and `fsyncdir`.
* All interactions with memory or disk go through `MemoryManager`. Inside it there is a write-back cache. The positions of garbage blocks will be reported to `MemoryManager` dynamically.

## 2) Code organization

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

## 3) Tasks

* Task 2: We add a read write lock for each file (i.e., each inode). To avoid disk accesses hindering memory operations, we have a disk lock and a memory lock. At one time a thread can only have at most one of them, so a thread waiting for I/O will not hold CPU.
* Task 3: There is a write-back cache in `MemoryManager`.
* Task 4: `MemoryManager` maintains a bitset to record whether each block is free. When a block becomes garbage, its position will be reported to `MemoryManager` immediately. When a new block needs to be written, `MemoryManager` will allocate a free block to it. When doing `sync`, `MemoryManager` will update this table to checkpoints.  

## 4) Test cases

* Test for task 2: Let one thread run `test-1` (in which there are many disk accesses) and another thread run other fast tests, it won't be blocked.
* Test for task 3: All tests in proj3 are still passed.
* Test for task 4: Run `test-1` (which creates and deletes large files) again and again, the FS does not crash and can still pass all tests.  