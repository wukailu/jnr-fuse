# Project 3: Log File System
> Group Member: 陈通, 吴凯路, 闫书弈
> Github repo: https://github.com/wukailu/jnr-fuse

## 1) Main Data Structures Of File System

* As described in paper of LFS, we have different types of data blocks. 
They are `Checkpoint`, `InodeMap`, `DataBlock`, `DirectoryBlock`, `Inode`, `Level1IndirectBlock`, and `Level2IndirectBlock`. They are all extended from `WritableObject` that have function `flush()` and `parse()`, which allow to turn them into bytes or parse from bytes.
* The metadata in stored in `Inode`, and the `Inode` object allows users to read or write data organized by this inode through `Inode.read()`, `Inode.write()`.  
* Build on these, we implement a LFS file system that can interact with the disk which is handling all reads, writes and changes for inode and data. To fast query address from inode, a large HashMap is used to map inode to physical address. Class `Logger` is designed to record all operations. Class `Tester` provide convenient ways to self-test. Class `MemoryManager` is designed to handle all read and writes to cache.
* For better OOP, we create class `MemoryFile` and `MemoryDirectory`, which can be regard as normal file and directory. For example, create a subdirectory in a directory only needs `parentDirectory.createDirectory(childName)`. 
* Only using interface from `MemoryFile` and `MemoryDirectory`, we implement functions from `jnr-fuse`, including `getattr`, `chmod`, `chown`, `utimens`, `access`, `create`, `mkdir`, `read`, `readdir`, `statfs`, `rename`, `rmdir`, `truncate`, `unlink`, `link`, `open`, `write`, `flush`, `fsync`, and `fsyncdir`.

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


## 3) Requirements 7-10

* Requirement 7: To fetch a file, we require the ancestor directories has `x` permission. For each operation, we check whether the file has the corresponding permissions. `chmod`, `chown` and `chgrp` can be executed if and only if you have write permission.

* Requirement 8: For each operation we implemented, it will call for the global lock in the beginning and release it in the end.

* Requirement 9: Whenever a `sync` operation (for any file or any directory) is called, we will flush our data from memory to the disk, using the function `writeToDisk` in `LogFS.java`.

* Requirement 10: When an operation is finished and the total size of new data in memory is large (over 1MB) (or when we call `sync`), we flush new data, new inode maps and checkpoints to the disk. When mounting again, the file system will read checkpoints and recover to this state. 

## 4) Test cases

* Test for read and write: In `test-1-create-write.sh` and `test-3-compile.sh` we create files with different size and generate a executable file using `g++`, respectively.

* Test for privilege: In `test-2-privilege.sh`, we construct a case to check the privilege.

* Test for hard links: In `test-4-hardlink.sh`, we build some hard links, then modify the files and `cat` other links.

* Test for `sync`: In `test-5-sync.sh`, we use `sync` and then restart the file system to see whether the file is correctly write to the disk.

* Test for multi-process: We use several terminals to run tests at the same time.

* Test for `block_dump`: In the `main` function in `LogFS.java`, we call `pretty_print` to print all metadata blocks right after we build the file system.

## 5) A User Manual for Non-standard Operations

* `block_dump` class is implemented in `LogFS.java`. Call the function `pretty_print` to print all metadata blocks.

## 6) Known bugs and limitations

* So far, there's no known bugs.
