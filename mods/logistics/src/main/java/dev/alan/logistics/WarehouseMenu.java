package dev.alan.logistics;

/** Implemented by every menu that shows the warehouse grid; the network handlers reach the shared state through it. */
public interface WarehouseMenu {
    WarehouseLink warehouse();
}
