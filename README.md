# metamorph

A Clojure library designed to providing pipelining operations.

### Pipeline operation

Pipeline operation is a function which accepts context as a map and returns possibly modified context map.

#### Context

Context is just a map where pipeline information is stored. There are three reserved keys which are supposed to help organize a pipeline:

* `:metamorph/data` - object which is subject to change and where the main data is stored. It can be anything: dataset, tensor, object, whatever you want
* `:metamorph/id` - unique operation number which is injected to the context just before pipeline operation is called. This way pipeline operation have some identity which can be used to store and restore private data in the context.
* `:metamorph/mode` - additional context information which can be used to determine pipeline phase. It can be added explicitely during pipeline creation.

#### Creating a pipeline

To create a pipeline you can use two functions:

* `pipeline` to make a pipeline function out of pipeline operators
* `->pipeline` as an above, but using declarations

## Usage

TODO!

## License

Copyright © 2021 Scicloj

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
