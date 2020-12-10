## Notes
### Commands
* `cat`:
* `ls`:
* `cp`:
* `mv`:
* `ln`:
* `chown`:
* `chmod`:

### Tests
* open after read

### Known bugs
* `rename()` is not correct: 
    * `mv a b`: the file will disapper
* `ACCESS` not implemented
* uid, gid should be long according to `chown()`.
* privilege check for a path

### Questions
* what should happen when calling `sync`?
* one user can belong to multiple group (use `id` to see), but there is only one gid in FuseContext.?

## Requirements

### Skeleton

(proj3 Task1&2)

* echo file system
* superblock, inode, directory, etc.;
* pretty_print function

### Basic Functions

(proj3 Task 3)

- `buildfs`: create a 100MB file for storage
- file status: size, timestamps
- create, list, delete
- modify a part, modify entire file, append something
- hard links
- standard permissions (UGO x RWX)
- (proj3 Task5) `tree`

### Crash Recovery

(proj3 T4)

* survive random crashes in the middle of any operation.
* fix the storage when mount the FS again.

### Multi-process

* (proj3, T4) thread safe: allowing multiple process to access
* (proj4, T2): concurrency: sleep when IO operations; achieve 100% CPU usage

### Cache

* (proj3, T4) `sync` : flush memory cache to disk
* (proj4, T3): write-back memory cache(4MB at max, some replacement policy)

### Garbage Collection

* in proj3, we do not need to recycle space 

* (proj4, T4): remove delete files and reclaim the disk space; thread-safe
* (proj4, T4+): concurrency 

## Document

* data structures of FS
* which source file contains which set of functionalities
* Some details on how you satisfy requirements tasks(permissions, thread support, memory buffer, reliability, garbage collection)
* Explanation of test cases
*  A user manual
* Known bugs and limitations.

2-4 pages, save our tests(unit tests & integration tests) in `tests/`.



Indirect block