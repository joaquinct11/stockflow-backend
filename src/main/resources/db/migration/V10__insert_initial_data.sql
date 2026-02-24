-- Insertar Roles
INSERT INTO roles (nombre, descripcion, created_at) VALUES
                                                        ('ADMIN', 'Administrador del sistema', NOW()),
                                                        ('VENDEDOR', 'Vendedor de productos', NOW()),
                                                        ('GESTOR_INVENTARIO', 'Gestor de inventario y stock', NOW()),
                                                        ('GERENTE', 'Gerente de farmacia', NOW());

-- Insertar Usuarios
INSERT INTO usuarios (email, contraseña, nombre, rol_id, activo, created_at, tenant_id) VALUES
                                                                                            ('admin@farmacia.com', '$2a$10$kBzOE9BxR24pAPFizoDGAeY1.b3V2ppwF4609khiWr8j5g9FObZKu', 'Admin Farmacia', 1, true, NOW(), 'farmacia-001'),
                                                                                            ('vendedor@farmacia.com', '$2a$10$/tPqlp3qm0DHuCA94ynqK.JDefr0pnodFTk.HtF19hN.39iaRYQQ6', 'Vendedor Principal', 2, true, NOW(), 'farmacia-001'),
                                                                                            ('gestor@farmacia.com', '$2a$10$0x5YA6SF6aHOLbfG8dsUDe97dTS1d4Xt2/Ne8C.g3dzs6JHGouTOi', 'Gestor Inventario', 3, true, NOW(), 'farmacia-001'),
                                                                                            ('gerente@farmacia.com', '$2a$10$mRFCU9orVZ5B36VIJ.OeG.28hrUK1B7ylh2W1ynOzuzrexETlOqoW', 'Gerente Farmacia', 4, true, NOW(), 'farmacia-001');

-- Insertar Proveedores
INSERT INTO proveedores (nombre, ruc, contacto, telefono, email, direccion, activo, tenant_id, created_at) VALUES
                                                                                                               ('Droguería Lima SAC', '20123456789', 'Juan Pérez', '01-4567890', 'ventas@droguerialima.com', 'Av. Argentina 1234, Lima', true, 'farmacia-001', NOW()),
                                                                                                               ('Farmacias Unidas EIRL', '20987654321', 'María González', '01-9876543', 'contacto@farmaciasunidas.com', 'Jr. Los Pinos 567, Lima', true, 'farmacia-001', NOW()),
                                                                                                               ('Distribuidora MediPharma SA', '20456789123', 'Carlos Rodríguez', '01-2345678', 'info@medipharma.com', 'Av. Colonial 890, Callao', true, 'farmacia-001', NOW()),
                                                                                                               ('Laboratorios Nacionales', '20789456123', 'Ana Torres', '01-5678901', 'pedidos@labnacionales.pe', 'Av. Industrial 456, Lima', true, 'farmacia-001', NOW()),
                                                                                                               ('Importadora Salud Global', '20321654987', 'Roberto Silva', '01-8765432', 'ventas@saludglobal.com', 'Av. Javier Prado 2345, San Isidro', true, 'farmacia-001', NOW());

-- Insertar Productos
INSERT INTO productos (nombre, codigo_barras, categoria, stock_actual, stock_minimo, stock_maximo, costo_unitario, precio_venta, lote, fecha_vencimiento, proveedor_id, activo, tenant_id, created_at) VALUES
                                                                                                                                                                                                           ('Paracetamol 500mg', '7501234567890', 'Analgésicos', 100, 20, 500, 2.50, 5.99, 'L12345', '2025-12-31', 1, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Ibuprofeno 400mg', '7501234567891', 'Antiinflamatorios', 50, 15, 300, 3.50, 7.99, 'L54321', '2025-10-15', 2, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Amoxicilina 500mg', '7501234567892', 'Antibióticos', 30, 10, 200, 5.00, 12.50, 'L99999', '2026-03-20', 3, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Loratadina 10mg', '7501234567893', 'Antihistamínicos', 75, 20, 400, 1.80, 4.50, 'L77777', '2025-08-10', 1, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Vitamina C 1000mg', '7501234567894', 'Vitaminas', 200, 50, 600, 1.20, 3.99, 'L88888', '2026-06-30', 4, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Omeprazol 20mg', '7501234567895', 'Gastrointestinales', 40, 10, 250, 4.50, 10.99, 'L11111', '2025-11-25', 2, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Metformina 500mg', '7501234567896', 'Endocrinos', 60, 15, 350, 3.00, 8.50, 'L22222', '2026-01-15', 5, true, 'farmacia-001', NOW()),
                                                                                                                                                                                                           ('Atorvastatina 10mg', '7501234567897', 'Cardiovasculares', 45, 10, 300, 6.00, 14.99, 'L33333', '2025-09-05', 3, true, 'farmacia-001', NOW());

-- Insertar Permisos
INSERT INTO permisos (nombre, descripcion, rol_id, created_at) VALUES
                                                                   ('CREAR_PRODUCTO', 'Permiso para crear productos', 1, NOW()),
                                                                   ('EDITAR_PRODUCTO', 'Permiso para editar productos', 1, NOW()),
                                                                   ('ELIMINAR_PRODUCTO', 'Permiso para eliminar productos', 1, NOW()),
                                                                   ('VER_REPORTES', 'Permiso para ver reportes', 1, NOW()),
                                                                   ('CREAR_VENTA', 'Permiso para crear ventas', 2, NOW()),
                                                                   ('VER_INVENTARIO', 'Permiso para ver inventario', 3, NOW()),
                                                                   ('GESTIONAR_USUARIOS', 'Permiso para gestionar usuarios', 1, NOW()),
                                                                   ('VER_VENTAS', 'Permiso para ver ventas', 4, NOW());

-- Insertar Suscripción
INSERT INTO suscripciones (usuario_principal_id, plan_id, precio_mensual, preapproval_id, estado, fecha_inicio, metodo_pago, ultimos_4_digitos, created_at) VALUES
    (1, 'PROFESIONAL', 99.99, 'PRE-001', 'ACTIVA', NOW(), 'TARJETA_CREDITO', '1234', NOW());