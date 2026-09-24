package br.com.myapp.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Barramento de eventos minimalista.
 *
 * Serve para desacoplar os módulos: o agendador avisa "lembrete disparou" sem
 * precisar conhecer a tela que vai mostrar o alerta. Assim da para acrescentar
 * módulos novos sem alterar os antigos.
 *
 * Os ouvintes são notificados na mesma thread que publicou o evento. Quem
 * precisa tocar a interface deve usar Platform.runLater dentro do próprio
 * ouvinte.
 */
public final class EventBus {

    private static final Map<Class<?>, List<Consumer<?>>> OUVINTES = new ConcurrentHashMap<>();

    private EventBus() {
    }

    /** Registra um ouvinte para um tipo de evento. */
    public static <T> void ouvir(Class<T> tipo, Consumer<T> ouvinte) {
        OUVINTES.computeIfAbsent(tipo, k -> new CopyOnWriteArrayList<>()).add(ouvinte);
    }

    /** Publica um evento para todos os ouvintes daquele tipo. */
    @SuppressWarnings("unchecked")
    public static <T> void publicar(T evento) {
        List<Consumer<?>> lista = OUVINTES.get(evento.getClass());
        if (lista == null) {
            return;
        }
        for (Consumer<?> c : lista) {
            try {
                ((Consumer<T>) c).accept(evento);
            } catch (Exception e) {
                // Um ouvinte com defeito não pode derrubar os demais.
                Log.erro("Falha ao processar evento " + evento.getClass().getSimpleName(), e);
            }
        }
    }
}
