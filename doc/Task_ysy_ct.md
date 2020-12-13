### 3) Requirements 7-10

* Requirement 7: To fetch a file, we require the ancestor directories has `x` permission. For each operation, we check whether the file has the corresponding permissions.

* Requirement 8: For each operation we implemented, it will call for the global lock in the beginning and release it in the end.

* Requirement 9: Whenever a `sync` operation (for any file or any directory) is called, we will flush our data from memory to the disk, using the function `writeToDisk` in `LogFS.java`.

* Requirement 10: When an operation is finished and the total size of new data in memory is large (over 1MB) (or when we call `sync`), we flush new data, new inode maps and checkpoints to the disk. When mounting again, the file system will read checkpoints and recover to this state. 

### 4) Test cases

* Test for read and write: In `test-1-create-write.sh` and `test-3-compile.sh` we create files with different size and generate a executable file using `g++`, respectively.

* Test for privilege: In `test-2-privilege.sh`, we construct a case to check the privilege.

* Test for hard links: In `test-4-hardlink.sh`, we build some hard links, then modify the files and `cat` other links.

* Test for `sync`: In `test-5-hardlink.sh`, we use `sync` and then restart the file system to see whether the file is correctly write to the disk.

* Test for multi-process: We use several terminals to run tests at the same time.

* Test for `block_dump`: In the `main` function in `LogFS.java`, we call `pretty_print` to print all metadata blocks right after we build the file system.

### 5) Non-standard operations

* `block_dump` class is implemented in `LogFS.java`. Call the function `pretty_print` to print all metadata blocks.