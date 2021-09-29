# API_Matching
Automatic Inference of Structural Changes for Matching across Program Versions

## Summary of API_Matching

Mapping code elements in one version of a program to corresponding code elements in another version is a fundamental building block for many software engineering tools. Existing tools that match code elements or identify structural changes--refactorings and API changes--between two versions of a program have two limitations that we overcome. First, existing tools cannot easily disambiguate among many potential matches or refactoring candidates. Second, it is difficult to use these tools' results for various software engineering tasks due to an unstructured representation of results. To overcome these limitations, our approach represents structural changes as a set of high-level change rules, automatically infers likely change rules and determines method-level matches based on the rules. By applying our tool to several open source projects, we show that our tool identifies matches that are difficult to find using other approaches and produces more concise results than other approaches. Our representation can serve as a better basis for other software engineering tools.

## Team

This project is developed by Professor [Miryung Kim](http://web.cs.ucla.edu/~miryung/)'s Software Engineering and Analysis Laboratory at UCLA. 
If you encounter any problems, please open an issue or feel free to contact us:

[Miryung Kim](http://web.cs.ucla.edu/~miryung/): Professor at UCLA, miryung@cs.ucla.edu;

## How to cite
Please refer to our ICSM '07 research paper [Automatic Inference of Structural Changes for Matching across Program Versions](http://web.cs.ucla.edu/~miryung/Publications/icse07-apirule.pdf) for more details

### BibTeX
```
@inproceedings{10.1109/ICSE.2007.20,
  author = {Kim, Miryung and Notkin, David and Grossman, Dan},
  title = {Automatic Inference of Structural Changes for Matching across Program Versions},
  year = {2007},
  isbn = {0769528287},
  publisher = {IEEE Computer Society},
  address = {USA},
  url = {https://doi.org/10.1109/ICSE.2007.20},
  doi = {10.1109/ICSE.2007.20},
  abstract = {Mapping code elements in one version of a program to corresponding code elements in
  another version is a fundamental building block for many software engineering tools.
  Existing tools that match code elements or identify structural changes--refactorings
  and API changes--between two versions of a program have two limitations that we overcome.
  First, existing tools cannot easily disambiguate among many potential matches or refactoring
  candidates. Second, it is difficult to use these tools' results for various software
  engineering tasks due to an unstructured representation of results. To overcome these
  limitations, our approach represents structural changes as a set of high-level change
  rules, automatically infers likely change rules and determines method-level matches
  based on the rules. By applying our tool to several open source projects, we show
  that our tool identifies matches that are difficult to find using other approaches
  and produces more concise results than other approaches. Our representation can serve
  as a better basis for other software engineering tools.},
  booktitle = {Proceedings of the 29th International Conference on Software Engineering},
  pages = {333–343},
  numpages = {11},
  series = {ICSE '07}
}
```
