# vm-translator

Foo.vm => Foo.asm & Foo.xml

Foo.xml 是语法分析结构。

如果 Foo 是一个文件夹的话，将文件夹下的所有 *.vm 一起编译到一个 Foo.asm 中。

为了保证编译器代码的可读性，生成 vm 代码时没有去处理一些冗余代码（像 call 和 return 代码都是可优化的）。