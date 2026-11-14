# Third-party components

Project code uses dependency-managed Spring Boot / Spring Framework / Spring Security / Spring Kafka, Hibernate, MySQL Connector/J and Apache PDFBox. Version declarations are in the Maven POMs. Tests use JUnit, Mockito and H2. Frontend uses Vue 3 and Vite, declared in package.json and the lockfile.

PDFBox: https://pdfbox.apache.org/ (Apache License 2.0). Embedded Noto Sans SC font: Google Fonts / Adobe, SIL Open Font License 1.1; see report-service/src/main/resources/fonts/OFL.txt and README.md. Font was instantiated at regular weight 400 with fontTools for export readability.

Existing Sony guide content is an editorial paraphrase of the official operating references linked in the guide. It is not manufacturer-certified inspection/grading content. Software QA fixtures use a reserved example.com link and are explicitly not real inspection advice; they are withdrawn/archived after verification.

Core code in this project was developed with AI assistance at the user's request. The project owner should review and understand the implementation and describe assistance/contributions according to the course's current policy.
