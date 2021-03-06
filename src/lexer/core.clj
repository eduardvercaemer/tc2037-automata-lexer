(ns lexer.core
  (:require [lexer.run :refer [run]]
            [lexer.html :refer [htmlize-tokens]])
  (:gen-class))

(def language
  {:start :stmt
   :transitions
   {;; statements, are either empty, comments, or assignemnts
    :stmt                    [{:where :end :to :halt}
                              {:where :ws :to :stmt}
                              {:where :newline :to :stmt :action :linebreak}
                              {:where :slash :to :cmt-0 :action :eat}
                              {:where :istart :to :asg-token :action :eat}]
    ;; comments begin with two slashes and end with a newline
    :cmt-0                   [{:where :slash :to :cmt-1 :action :eat}]
    :cmt-1                   [{:where :end :to :halt :action :out-comment}
                              {:where :return :to :cmt-1}
                              {:where :newline :to :stmt :action [:out-comment :linebreak]}
                              {:where :any :to :cmt-1 :action :eat}]
    ;; assignments begin with a token
    :asg-token               [{:where :irest :to :asg-token :action :eat}
                              {:where :ws :to :asg-equal :action :out-token}
                              {:where :equal :to :asg-expr :action [:out-token :out-equal]}]
    ;; then have an assignment operator
    :asg-equal               [{:where :equal :to :asg-expr :action :out-equal}
                              {:where :ws :to :asg-equal}]
    ;; then have an expression
    :asg-expr                [{:where :num :to :expr-num :action :eat}
                              {:where :istart :to :expr-token :action :eat}
                              {:where :minus :to :asg-expr-m :action :eat}
                              {:where :oparen :to :asg-expr :action [:add-paren :out-oparen]}
                              {:where :ws :to :asg-expr}]
    ;; this allows for negative values (unary '-' operator)
    :asg-expr-m              [{:where :num :to :expr-num :action :eat}
                              {:where :istart :to :expr-token :action :eat}
                              {:where :oparen :to :asg-expr :action [:out-op :add-paren :out-oparen]}
                              {:where :ws :to :asg-expr-m}]
    ;; expressions have numeric parts
    :expr-num                [{:where :end :to :halt :action [:check-paren :out-num]}
                              {:where :dot :to :expr-float :action :eat}
                              {:where :num :to :expr-num :action :eat}
                              {:where :newline :to :stmt :action [:check-paren :out-num :linebreak]}
                              {:where :slash :to :expr-cmt-0 :action [:out-num :eat]}
                              {:where :op :to :asg-expr :action [:out-num :eat :out-op]}
                              {:where :cparen :to :expr-op :action [:del-paren :out-num :out-cparen]}
                              {:where :ws :to :expr-op :action :out-num}]
    ;; floats are slightly more complex numbers with a decimal part
    :expr-float              [{:where :num :to :expr-float-rest :action :eat}]
    :expr-float-rest         [{:where :end :to :halt :action [:check-paren :out-float]}
                              {:where :e-notation :to :expr-float-exponent :action :eat}
                              {:where :num :to :expr-float-rest :action :eat}
                              {:where :newline :to :stmt :action [:check-paren :out-float :linebreak]}
                              {:where :slash :to :expr-cmt-0 :action [:out-float :eat]}
                              {:where :op :to :asg-expr :action [:out-float :eat :out-op]}
                              {:where :cparen :to :expr-op :action [:del-paren :out-float :out-cparen]}
                              {:where :ws :to :expr-op :action :out-float}]
    :expr-float-exponent     [{:where :num :to :expr-float-exponent-r :action :eat}
                              {:where :minus :to :expr-float-exponent-n :action :eat}]
    :expr-float-exponent-n   [{:where :num :to :expr-float-exponent-r :action :eat}]
    :expr-float-exponent-r   [{:where :end :to :halt :action [:check-paren :out-float]}
                              {:where :num :to :expr-float-exponent-r :action :eat}
                              {:where :newline :to :stmt :action [:check-paren :out-float :linebreak]}
                              {:where :slash :to :expr-cmt-0 :action [:out-float :eat]}
                              {:where :op :to :asg-expr :action [:out-float :eat :out-op]}
                              {:where :cparen :to :expr-op :action [:del-paren :out-float :out-cparen]}
                              {:where :ws :to :expr-op :action :out-float}]
    ;; expressions can also have tokens instead of a numeric part
    :expr-token              [{:where :end :to :halt :action [:check-paren :out-token]}
                              {:where :irest :to :expr-token :action :eat}
                              {:where :newline :to :stmt :action [:check-paren :out-token :linebreak]}
                              {:where :slash :to :expr-cmt-0 :action [:out-num :eat]}
                              {:where :op :to :asg-expr :action [:out-token :eat :out-op]}
                              {:where :cparen :to :expr-op :action [:del-paren :out-token :out-cparen]}
                              {:where :ws :to :expr-op :action :out-token}]
    ;; expressions can end optionally by comments, which can be a little confused by division '/'
    :expr-cmt-0              [{:where :slash :to :cmt-1 :action [:check-paren :eat]}
                              {:where :num :to :expr-num :action [:out-op :eat]}
                              {:where :istart :to :expr-token :action [:out-op :eat]}
                              {:where :oparen :to :asg-expr :action [:out-op :add-paren :out-oparen]}
                              {:where :ws :to :asg-expr :action :out-op}]
    ;; separated by operators
    :expr-op                 [{:where :end :to :halt :action :check-paren}
                              {:where :ws :to :expr-op}
                              {:where :newline :to :stmt :action [:check-paren :linebreak]}
                              {:where :cparen :to :expr-op :action [:del-paren :out-cparen]}
                              {:where :slash :to :expr-cmt-0 :action :eat}
                              {:where :op :to :asg-expr :action [:eat :out-op]}]}})

(defn- lex-input
  "Read an input file and produce a list of tokens"
  [filename]
  (->>
   filename
   slurp
   (run language)))

(defn- show-tokens
  "Prints information on a list of tokens"
  [tokens]
  (->>
   tokens
   (doseq [{kind :kind value :value} tokens]
     (println (str "Token: " kind " " value)))))

(defn- main-console
  "Read and lex input file with language definition"
  [& _]
  (print "Input filename: ")
  (flush)
  (->>
   (read-line)
   lex-input
   show-tokens))

(defn- main-htmlize
  [& _]
  (let [input-filename (do
                         (print "input filename: ")
                         (flush)
                         (read-line))
        output-filename (do
                          (print "ooutput filename: ")
                          (flush)
                          (read-line))
        tokens (lex-input input-filename)
        html (htmlize-tokens tokens)]
    (spit output-filename html)))

(defn -main
  "Read and lex input file with language definition"
  [& _]
  (main-htmlize))
