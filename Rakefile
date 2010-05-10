
task :test do
	puts "Hello"
	classpath = []
	classpath << "test" << "src/clj" << "classes"
	classpath << Dir.new("lib").entries.select{|f| f =~ /.*.jar/}.map {|jar| File.join('lib', jar)}
	puts classpath
	system("java -cp #{classpath.join(':')} clojure.main test/run.clj --no-server")
end