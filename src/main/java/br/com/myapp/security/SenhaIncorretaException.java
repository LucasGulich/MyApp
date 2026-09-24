package br.com.myapp.security;

/** Lancada quando a senha mestra não abre o cofre ou o dado foi adulterado. */
public class SenhaIncorretaException extends RuntimeException {
    public SenhaIncorretaException(String mensagem) {
        super(mensagem);
    }
}
