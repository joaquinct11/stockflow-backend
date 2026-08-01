-- V87: Limpia duplicados causados por deploy concurrente (rolling deploy de Render).
-- El DigemidCatalogLoader recargará los 18,481 productos correctamente al arrancar.
TRUNCATE TABLE catalogo_digemid RESTART IDENTITY;
